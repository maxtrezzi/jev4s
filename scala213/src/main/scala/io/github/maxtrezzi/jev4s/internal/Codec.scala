package io.github.maxtrezzi.jev4s
package internal

import scala.util.Try

/** The answers of a successful reply, in question order, and its metadata. */
private[jev4s] final case class Decoded(answers: List[Answer], reply: Reply)

/** Turns questions into the JSON Jev reads, and Jev's JSON back into answers. All JSON handling
  * lives here. Format: https://docs.typesafe.ai/api.md, checked against the files in `golden/`.
  */
private[jev4s] object Codec {

  /** The body of `POST /v1/systemone`. Questions keep their order. */
  def encode(model: String, state: ujson.Value, questions: List[(String, Question[_])]): ujson.Value =
    ujson.Obj(
      "state"     -> state,
      "model"     -> model,
      "questions" -> ujson.Obj.from(questions.map { case (name, question) => name -> encodeQuestion(question) })
    )

  /** The answers of a successful reply, in the order of `questions`, and the reply's metadata. The
    * order of the answers in the JSON does not matter.
    */
  def decode(body: String, questions: List[(String, Question[_])]): Either[JevError, Decoded] = {
    val decoded = for {
      json    <- Try(ujson.read(body)).toOption.toRight("the response is not JSON")
      answers <- field(json, "answers")
      list    <- traverse(questions) { case (name, question) =>
        field(answers, name).flatMap(decodeAnswer(name, question, _))
      }
      model  <- field(json, "model").flatMap(string(_, "model"))
      tokens <- field(json, "usage").flatMap(field(_, "input_tokens")).flatMap(long(_, "input_tokens"))
    } yield Decoded(list, Reply(model, tokens))
    decoded.left.map(JevError.Decoding)
  }

  /** The message of an error body. A 401 carries `{"detail": {"message": ...}}`; a 422 carries
    * `{"detail": [{"loc": [...], "msg": ...}]}`. Anything else comes back as it is, cut to
    * 200 characters: the body of a proxy's error page can be a whole HTML page.
    */
  def errorMessage(body: String): String =
    Try(ujson.read(body)).toOption.flatMap(_.objOpt).flatMap(_.get("detail")) match {
      case Some(ujson.Obj(detail)) => detail.get("message").flatMap(_.strOpt).getOrElse(raw(body))
      case Some(ujson.Arr(items))  => items.flatMap(_.objOpt).map(locatedMessage).mkString("; ")
      case _                       => raw(body)
    }

  private def raw(body: String): String = if (body.length <= 200) body else body.take(200) + "…"

  private def locatedMessage(item: collection.Map[String, ujson.Value]): String = {
    val loc = item.get("loc").flatMap(_.arrOpt).map(_.map(locPart).mkString(".")).getOrElse("?")
    val msg = item.get("msg").flatMap(_.strOpt).getOrElse("?")
    s"$loc: $msg"
  }

  /** A part of a 422 location: a field name, or the index of a list item. */
  private def locPart(part: ujson.Value): String = part match {
    case ujson.Str(s)              => s
    case ujson.Num(n) if n.isWhole => n.toLong.toString
    case other                     => other.render()
  }

  private def encodeQuestion(question: Question[_]): ujson.Value = question match {
    case Noul(instructions, whenTrue, whenFalse) =>
      val criteria = whenTrue.map("true" -> _).toList ++ whenFalse.map("false" -> _)
      val base     = ujson.Obj("type" -> "noul", "instructions" -> instructions)
      if (criteria.nonEmpty) base("criteria") = ujson.Obj.from(criteria)
      base
    case Score(instructions, levels) =>
      ujson.Obj("type" -> "score", "instructions" -> instructions, "criteria" -> ujson.Arr.from(levels.map(_.text)))
    case Choice(instructions, options) =>
      val criteria = options.map(o => o.key -> o.description.getOrElse(ujson.Null))
      ujson.Obj("type" -> "choice", "instructions" -> instructions, "criteria" -> ujson.Obj.from(criteria))
  }

  private def decodeAnswer(name: String, question: Question[_], json: ujson.Value): Either[String, Answer] =
    question match {
      case Noul(_, _, _) =>
        for {
          _ <- hasType(json, "noul", name)
          p <- field(json, "noul").flatMap(probability(_, name))
        } yield NoulAnswer(p)
      case s: Score[_]  => decodeScore(name, s, json)
      case c: Choice[_] => decodeChoice(name, c, json)
    }

  private def decodeScore[L](name: String, question: Score[L], json: ujson.Value): Either[String, Answer] = {
    val values = question.levels.map(_.value)
    for {
      _             <- hasType(json, "score", name)
      score         <- field(json, "score").flatMap(number(_, name))
      confidence    <- field(json, "confidence").flatMap(probability(_, name))
      probabilities <- probabilitiesOf(json, name)(index => index.toIntOption.flatMap(values.lift))
      // maxByOption keeps the first of equal maxima: a tie goes to the lower level.
      mostLikely <- values
        .filter(probabilities.contains)
        .maxByOption(probabilities)
        .toRight(s"'$name': no probabilities")
    } yield ScoreAnswer(score, mostLikely, confidence, probabilities)
  }

  private def decodeChoice[C](name: String, question: Choice[C], json: ujson.Value): Either[String, Answer] = {
    val byKey = question.options.map(o => o.key -> o.value).toMap
    for {
      _             <- hasType(json, "choice", name)
      key           <- field(json, "choice").flatMap(string(_, name))
      choice        <- byKey.get(key).toRight(s"'$name': '$key' is not one of its options")
      confidence    <- field(json, "confidence").flatMap(probability(_, name))
      probabilities <- probabilitiesOf(json, name)(byKey.get)
    } yield ChoiceAnswer(choice, confidence, probabilities)
  }

  private def field(json: ujson.Value, name: String): Either[String, ujson.Value] =
    json.objOpt.flatMap(_.get(name)).toRight(s"missing field '$name'")

  private def string(json: ujson.Value, name: String): Either[String, String] =
    json.strOpt.toRight(s"'$name': expected a string, got $json")

  private def number(json: ujson.Value, name: String): Either[String, Double] =
    json.numOpt.toRight(s"'$name': expected a number, got $json")

  private def long(json: ujson.Value, name: String): Either[String, Long] =
    json.numOpt.filter(_.isWhole).map(_.toLong).toRight(s"'$name': expected a whole number, got $json")

  private def probability(json: ujson.Value, name: String): Either[String, Probability] =
    number(json, name).flatMap(d => Probability.from(d).toRight(s"'$name': $d is not a probability"))

  private def hasType(json: ujson.Value, expected: String, name: String): Either[String, Unit] =
    field(json, "type").flatMap(string(_, name)).flatMap { found =>
      Either.cond(found == expected, (), s"'$name': expected a $expected answer, got $found")
    }

  /** The `probabilities` object, each key turned into a `K` by `key`. */
  private def probabilitiesOf[K](json: ujson.Value, name: String)(
      key: String => Option[K]
  ): Either[String, Map[K, Probability]] =
    for {
      obj   <- field(json, "probabilities").flatMap(_.objOpt.toRight(s"'$name': probabilities is not an object"))
      pairs <- traverse(obj.toList) { case (k, v) =>
        for {
          kk <- key(k).toRight(s"'$name': unknown key '$k' in probabilities")
          p  <- probability(v, name)
        } yield kk -> p
      }
    } yield pairs.toMap

  /** The results of `f`, or its first error. */
  private def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
    as.foldRight[Either[String, List[B]]](Right(Nil))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))
}
