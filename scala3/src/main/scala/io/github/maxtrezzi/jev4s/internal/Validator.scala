package io.github.maxtrezzi.jev4s
package internal

/** Checks a request before it is sent, and returns every problem at once, so one failed request
  * shows everything there is to fix.
  */
private[jev4s] object Validator:

  def validate(questions: List[(String, Question[?])]): List[Problem] =
    val none = if questions.isEmpty then List(Problem.NoQuestions) else Nil
    none ++ repeated(questions.map(_._1)).map(Problem.DuplicateName(_)) ++ questions.flatMap(check)

  private def check(name: String, question: Question[?]): List[Problem] =
    val nameProblem = if name.trim.isEmpty then List(Problem.EmptyName) else Nil
    nameProblem ++ (question match
      case Question.Score(_, levels) if levels.size < 2 || levels.size > 10 =>
        List(Problem.ScoreLevels(name, levels.size))
      case Question.Score(_, levels) =>
        repeated(levels.map(_.text)).map(text => Problem.DuplicateLevel(name, text.strOpt.getOrElse(text.render()))) ++
          sharedValues(levels.map(l => l.value -> l.text)).map(v => Problem.DuplicateLevelValue(name, v.toString))
      case Question.Choice(_, options) =>
        val keys = options.map(_.key)
        if keys.isEmpty || keys.size > 255 then List(Problem.ChoiceOptions(name, keys.size))
        else
          repeated(keys).map(Problem.DuplicateOptionKey(name, _)) ++
            sharedValues(options.map(o => o.value -> o.key)).map(v => Problem.DuplicateOptionValue(name, v.toString))
      case _ => Nil)

  /** Each value given to more than one level or option, whose text or key differs: a repeated
    * text or key alone is reported by its own problem. In order of the value's second occurrence.
    */
  private def sharedValues[V, T](pairs: List[(V, T)]): List[V] =
    val textsOf = pairs.groupMap(_._1)(_._2)
    repeated(pairs.map(_._1)).filter(textsOf(_).distinct.size > 1)

  /** Each value that occurs more than once, once, in order of its second occurrence. */
  private def repeated[A](values: List[A]): List[A] = values.diff(values.distinct).distinct
