package io.github.maxtrezzi.jev4s
package internal

/** Checks a request before it is sent, and returns every problem at once, so one failed request
  * shows everything there is to fix.
  */
private[jev4s] object Validator {

  def validate(questions: List[(String, Question[_])]): List[Problem] = {
    val none = if (questions.isEmpty) List(Problem.NoQuestions) else Nil
    none ++ repeated(questions.map(_._1)).map(Problem.DuplicateName) ++ questions.flatMap { case (n, q) => check(n, q) }
  }

  private def check(name: String, question: Question[_]): List[Problem] = {
    val nameProblem = if (name.trim.isEmpty) List(Problem.EmptyName) else Nil
    nameProblem ++ (question match {
      case Score(_, levels) if levels.size < 2 || levels.size > 10 =>
        List(Problem.ScoreLevels(name, levels.size))
      case Score(_, levels) =>
        repeated(levels.map(_.text)).map(text => Problem.DuplicateLevel(name, text.strOpt.getOrElse(text.render())))
      case Choice(_, options) =>
        val keys = options.map(_.key)
        if (keys.isEmpty || keys.size > 255) List(Problem.ChoiceOptions(name, keys.size))
        else repeated(keys).map(Problem.DuplicateOptionKey(name, _))
      case _ => Nil
    })
  }

  /** Each value that occurs more than once, once, in order of its second occurrence. */
  private def repeated[A](values: List[A]): List[A] = values.diff(values.distinct).distinct
}
