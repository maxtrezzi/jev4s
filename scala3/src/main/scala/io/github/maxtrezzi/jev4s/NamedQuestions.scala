package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** The answer type of a question type: `NoulAnswer` for a `Noul`, `ChoiceAnswer[C]` for a
  * `Choice[C]`.
  */
type AnswerOf[Q] <: Answer = Q match
  case Question[a] => a

/** Evidence that every value of a named tuple is a question, found by the compiler. */
@implicitNotFound("every value in the named tuple must be a question: Noul, Score or Choice")
sealed trait AllQuestions[T <: Tuple]:
  def list(values: T): List[Question[?]]

object AllQuestions:
  given AllQuestions[EmptyTuple]:
    def list(values: EmptyTuple): List[Question[?]] = Nil

  given [H <: Question[?], T <: Tuple] => (rest: AllQuestions[T]) => AllQuestions[H *: T]:
    def list(values: H *: T): List[Question[?]] = values.head :: rest.list(values.tail)

/** The names of a named tuple, as strings, found by the compiler. */
@implicitNotFound("the questions must be a named tuple, such as (urgent = Noul(\"Is it urgent?\")).")
sealed trait QuestionNames[N <: Tuple]:
  def list: List[String]

object QuestionNames:
  given QuestionNames[EmptyTuple]:
    def list: List[String] = Nil

  given [H <: String, T <: Tuple] => (name: ValueOf[H], rest: QuestionNames[T]) => QuestionNames[H *: T]:
    def list: List[String] = name.value :: rest.list
