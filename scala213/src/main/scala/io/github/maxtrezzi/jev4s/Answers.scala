package io.github.maxtrezzi.jev4s

/** The answers of one request, read with the keys that asked them. */
final class Answers private[jev4s] (byName: Map[String, (Question[_], Answer)]) {

  /** The answer asked by `key`, or `None` when no question was asked under its name, or when
    * the question asked under that name is not the key's question. The second case catches a key
    * built for a different request, whose answer would have the wrong type.
    */
  def get[A <: Answer](key: Key[A]): Option[A] =
    byName.get(key.name).collect {
      // The questions are equal, part by part of the same class, so the answer is the one
      // `key.question` has: an `A`.
      case (question, answer) if Answers.same(question, key.question) => answer.asInstanceOf[A]
    }
}

private object Answers {

  /** `a == b`, and each part of `a` has the class of the same part of `b`. Scala's `==` says
    * `1 == 1L`, so without the second check a `Choice[Int]` and a `Choice[Long]` with the same
    * options would be the same question.
    */
  def same(a: Any, b: Any): Boolean = a == b && sameClasses(a, b)

  private def sameClasses(a: Any, b: Any): Boolean =
    classOf(a) == classOf(b) && ((a, b) match {
      case (x: Iterable[_], y: Iterable[_]) => x.zip(y).forall { case (p, q) => sameClasses(p, q) }
      case (x: Product, y: Product)         =>
        x.productIterator.zip(y.productIterator).forall { case (p, q) => sameClasses(p, q) }
      case _ => true
    })

  private def classOf(value: Any): Option[Class[_]] = Option(value).map(_.getClass)
}
