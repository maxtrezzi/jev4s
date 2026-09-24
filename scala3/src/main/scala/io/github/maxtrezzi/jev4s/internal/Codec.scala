package io.github.maxtrezzi.jev4s
package internal

import scala.util.Try

/** Turns questions into the JSON Jev reads, and Jev's JSON back into answers. All JSON handling
  * lives here. Format: https://docs.typesafe.ai/api.md, checked against the files in `golden/`.
  */
private[jev4s] object Codec:

  /** The body of `POST /v1/systemone`. Questions keep their order. */
  def encode(model: String, state: ujson.Value, questions: List[(String, Question[?])]): ujson.Value =
    ujson.Obj(
      "state"     -> state,
      "model"     -> model,
      "questions" -> ujson.Obj.from(questions.map((name, question) => name -> encodeQuestion(question))),
    )

  /** The answers of a successful reply, in the order of `questions`, and the reply's metadata. The
    * order of the answers in the JSON does not matter.
    */
  def decode(
      body: String,
      questions: List[(String, Question[?])],
  ): Either[JevError, (answers: List[Answer], reply: Reply)] =
    val decoded =
      for
        json    <- Try(ujson.read(body)).toOption.toRight("the response is not JSON")
        answers <- json.field("answers")
        list    <- traverse(questions)((name, question) => answers.field(name).flatMap(decodeAnswer(name, question, _)))
        model   <- json.field("model").flatMap(_.string("model"))
        // Missing tokens cost no answers (ADR-0035).
        tokens = json.field("usage").flatMap(_.field("input_tokens")).toOption.flatMap(_.wholeNumber)
      yield (answers = list, reply = Reply(model, tokens))
    decoded.left.map(JevError.Decoding(_))

  /** The message of an error body. A 401 carries `{"detail": {"message": ...}}`; a 422 carries
    * `{"detail": [{"loc": [...], "msg": ...}]}`. Anything else comes back as it is, cut to
    * 200 characters: the body of a proxy's error page can be a whole HTML page.
    */
  def errorMessage(body: String): String =
    Try(ujson.read(body)).toOption.flatMap(_.objOpt).flatMap(_.get("detail")) match
      case Some(ujson.Obj(detail)) => detail.get("message").flatMap(_.strOpt).getOrElse(raw(body))
      case Some(ujson.Arr(items))  => items.flatMap(_.objOpt).map(locatedMessage).mkString("; ")
      case _                       => raw(body)

  private def raw(body: String): String = if body.length <= 200 then body else body.take(200) + "…"

  private def locatedMessage(item: collection.Map[String, ujson.Value]): String =
    val loc = item.get("loc").flatMap(_.arrOpt).map(_.map(locPart).mkString(".")).getOrElse("?")
    val msg = item.get("msg").flatMap(_.strOpt).getOrElse("?")
    s"$loc: $msg"

  /** A part of a 422 location: a field name, or the index of a list item. */
  private def locPart(part: ujson.Value): String = part match
    case ujson.Str(s)              => s
    case ujson.Num(n) if n.isWhole => n.toLong.toString
    case other                     => other.render()

  private def encodeQuestion(question: Question[?]): ujson.Value = question match
    case Question.Noul(instructions, whenTrue, whenFalse) =>
      val criteria = whenTrue.map("true" -> _).toList ++ whenFalse.map("false" -> _)
      val base     = ujson.Obj("type" -> "noul", "instructions" -> instructions)
      if criteria.nonEmpty then base("criteria") = ujson.Obj.from(criteria)
      base
    case Question.Score(instructions, levels) =>
      ujson.Obj("type" -> "score", "instructions" -> instructions, "criteria" -> ujson.Arr.from(levels.map(_.text)))
    case Question.Choice(instructions, options) =>
      val criteria = options.map(o => o.key -> o.description.getOrElse(ujson.Null))
      ujson.Obj("type" -> "choice", "instructions" -> instructions, "criteria" -> ujson.Obj.from(criteria))

  /** The answer to one question. Matching on the question refines `A`, so each branch returns the
    * answer type of its own question, with no cast.
    */
  private def decodeAnswer[A <: Answer](name: String, question: Question[A], json: ujson.Value): Either[String, A] =
    question match
      case Question.Noul(_, _, _) =>
        for
          _ <- json.hasType("noul", name)
          p <- json.field("noul").flatMap(_.probability(name))
        yield NoulAnswer(p)
      case Question.Score(_, levels) =>
        val values = levels.map(_.value)
        for
          _             <- json.hasType("score", name)
          score         <- json.field("score").flatMap(_.number(name))
          confidence    <- json.field("confidence").flatMap(_.probability(name))
          probabilities <- json.probabilities(name)(index => index.toIntOption.flatMap(values.lift))
          // maxByOption keeps the first of equal maxima: a tie goes to the lower level.
          mostLikely <- values
            .filter(probabilities.contains)
            .maxByOption(probabilities)
            .toRight(s"'$name': no probabilities")
        yield ScoreAnswer(score, mostLikely, confidence, probabilities)
      case Question.Choice(_, options) =>
        val byKey = options.map(o => o.key -> o.value).toMap
        for
          _             <- json.hasType("choice", name)
          key           <- json.field("choice").flatMap(_.string(name))
          choice        <- byKey.get(key).toRight(s"'$name': '$key' is not one of its options")
          confidence    <- json.field("confidence").flatMap(_.probability(name))
          probabilities <- json.probabilities(name)(byKey.get)
        yield ChoiceAnswer(choice, confidence, probabilities)

  extension (json: ujson.Value)
    private def field(name: String): Either[String, ujson.Value] =
      json.objOpt.flatMap(_.get(name)).toRight(s"missing field '$name'")
    private def string(name: String): Either[String, String] =
      json.strOpt.toRight(s"'$name': expected a string, got $json")
    private def number(name: String): Either[String, Double] =
      json.numOpt.toRight(s"'$name': expected a number, got $json")
    private def wholeNumber: Option[Long]                              = json.numOpt.filter(_.isWhole).map(_.toLong)
    private def probability(name: String): Either[String, Probability] =
      number(name).flatMap(d => Probability.from(d).toRight(s"'$name': $d is not a probability"))
    private def hasType(expected: String, name: String): Either[String, Unit] =
      field("type")
        .flatMap(_.string(name))
        .flatMap: found =>
          Either.cond(found == expected, (), s"'$name': expected a $expected answer, got $found")

    /** The `probabilities` object, each key turned into a `K` by `key`. */
    private def probabilities[K](name: String)(key: String => Option[K]): Either[String, Map[K, Probability]] =
      for
        obj   <- field("probabilities").flatMap(_.objOpt.toRight(s"'$name': probabilities is not an object"))
        pairs <- traverse(obj.toList): (k, v) =>
          for
            kk <- key(k).toRight(s"'$name': unknown key '$k' in probabilities")
            p  <- v.probability(name)
          yield kk -> p
      yield pairs.toMap

  /** The results of `f`, or its first error. */
  private def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.foldRight[Either[String, List[B]]](Right(Nil))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))
