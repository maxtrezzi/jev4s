package io.github.maxtrezzi.jev4s
package internal

/** Checks a request before it is sent, and returns every problem at once, so one failed request
  * shows everything there is to fix.
  */
private[jev4s] object Validator {

  def validate(
      questions: List[(String, Question[_])],
      state: ujson.Value = ujson.Null,
      images: List[Image] = Nil
  ): List[Problem] = {
    val none = if (questions.isEmpty) List(Problem.NoQuestions) else Nil
    none ++ repeated(questions.map(_._1)).map(Problem.DuplicateName) ++ questions.flatMap { case (n, q) =>
      check(n, q)
    } ++ boxes(questions, images) ++ references(state, images)
  }

  /** Each Box of a request with no image: Jev answers it anyway, with a box it made up. */
  private def boxes(questions: List[(String, Question[_])], images: List[Image]): List[Problem] =
    if (images.nonEmpty) Nil
    else questions.collect { case (name, Box(_)) => Problem.BoxWithoutImage(name) }

  /** Each image ref in the state with no image, and each image whose ref is not in the state. The
    * API refuses an image that nothing uses ("Invalid image request.", measured 2026-11-01).
    */
  private def references(state: ujson.Value, images: List[Image]): List[Problem] = {
    val used = strings(state).flatMap(Ref.findAllIn(_)).distinct
    val sent = images.map(_.ref).distinct
    used.filterNot(sent.contains).map(Problem.UnknownImage) ++ sent.filterNot(used.contains).map(Problem.UnusedImage)
  }

  /** The shape of [[Image.ref]]. A `def`, not a `val`: Stryker4s cannot mutate a value initialised
    * once (ADR-0020), and this one costs nothing to build again.
    */
  private def Ref = "image-[0-9a-f]{32}".r

  /** Every string in the JSON, where the API looks for image refs. */
  private def strings(json: ujson.Value): List[String] = json match {
    case ujson.Str(s) => List(s)
    case ujson.Arr(a) => a.toList.flatMap(strings)
    case ujson.Obj(o) => o.values.toList.flatMap(strings)
    case _            => Nil
  }

  private def check(name: String, question: Question[_]): List[Problem] = {
    val nameProblem = if (name.trim.isEmpty) List(Problem.EmptyName) else Nil
    nameProblem ++ (question match {
      case Score(_, levels) if levels.size < 2 || levels.size > 10 =>
        List(Problem.ScoreLevels(name, levels.size))
      case Score(_, levels) =>
        repeated(levels.map(_.text)).map(text => Problem.DuplicateLevel(name, text.strOpt.getOrElse(text.render()))) ++
          sharedValues(levels.map(l => l.value -> l.text)).map(v => Problem.DuplicateLevelValue(name, v.toString))
      case Choice(_, options) =>
        val keys = options.map(_.key)
        if (keys.isEmpty || keys.size > 255) List(Problem.ChoiceOptions(name, keys.size))
        else
          repeated(keys).map(Problem.DuplicateOptionKey(name, _)) ++
            sharedValues(options.map(o => o.value -> o.key)).map(v => Problem.DuplicateOptionValue(name, v.toString))
      case _ => Nil
    })
  }

  /** Each value given to more than one level or option, whose text or key differs: a repeated
    * text or key alone is reported by its own problem. In order of the value's second occurrence.
    */
  private def sharedValues[V, T](pairs: List[(V, T)]): List[V] = {
    val textsOf = pairs.groupMap(_._1)(_._2)
    repeated(pairs.map(_._1)).filter(textsOf(_).distinct.size > 1)
  }

  /** Each value that occurs more than once, once, in order of its second occurrence. */
  private def repeated[A](values: List[A]): List[A] = values.diff(values.distinct).distinct
}
