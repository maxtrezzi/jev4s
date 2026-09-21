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
      case Question.Score(_, levels*) if levels.size < 2 || levels.size > 10 =>
        List(Problem.ScoreLevels(name, levels.size))
      case choice: Question.Choice[?] =>
        val keys = choice.choices.options.map(_.key)
        if keys.isEmpty || keys.size > 255 then List(Problem.ChoiceOptions(name, keys.size))
        else repeated(keys).map(Problem.DuplicateOptionKey(name, _))
      case _ => Nil
    )

  /** Each value that occurs more than once, once, in order of its second occurrence. */
  private def repeated(values: List[String]): List[String] = values.diff(values.distinct).distinct
