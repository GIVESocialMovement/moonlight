package givers.moonlight.util

import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.internal.creation.MockSettingsImpl
import org.mockito.invocation.InvocationOnMock
import org.mockito.quality.Strictness
import org.mockito.stubbing.{Answer, OngoingStubbing}
import org.scalatest.{AsyncTestSuite, FutureOutcome, Suite}
import org.scalatestplus.mockito.MockitoSugar

import scala.quoted.*
import scala.reflect.ClassTag

object AsyncIdiomaticMockito:
  // the macro provides "returning" infix from original idiomatic mockito.
  // Besides that the Mockito doesn't like when you mix raw arguments and matchers like
  // mock.someFn(1, *) so the macro wraps all raw arguments with eq function call
  def returnsMacro[T : Type](mockCall: Expr[T], requiredReturnValue: Expr[T])(using Quotes): Expr[OngoingStubbing[T]] = {
    val quotes = summon[Quotes]
    import quotes.reflect.*
    import quotes.*

    // diving to syntax level because it's difficult to pattern match expr like $object.$method($arguments*) in Expr level
    val tree = mockCall.asTerm

    val updatedMockCall = tree match
      case app@Inlined(a, b, Apply(fun, args)) =>
        val newArgTerms = args.map {
          arg =>

            // Example
            // scala.Int.int2long(org.mockito.ArgumentMatchers.eq(firstId))
            // org.mockito.ArgumentMatchers.eq(1)
            // JobDispatcherSpec.this.*[java.util.Date]
            val argString = arg.show

            // since calling eq/* may be not in starting position we just check if something presents in string representation

            // explicit call of eq or using * as ANY matcher
            if(argString.contains("org.mockito.ArgumentMatchers.eq") || argString.contains(".*[")) {
              // in this case we're going to use original argument
              arg
            } else {
              val argExpression = arg.asExpr

              // we need to determine the actual type because "eq" method has 9 overloads
              // and without type specification it will pick eq[T] (because asExpr returns Any) which will cause wrong behavior
              val emd = argExpression match
                case '{ $field: Boolean } => '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Byte } => '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Char } =>  '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Short } =>  '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Int } =>  '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Long } =>  '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Double } =>  '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: Float } =>  '{org.mockito.ArgumentMatchers.eq($field)}
                case '{ $field: t } =>  '{org.mockito.ArgumentMatchers.eq[t]($field)}

              emd.asTerm
            }
        }
        Inlined(a, b, Apply(fun, newArgTerms))
      case other =>
        other

    '{ _root_.org.mockito.Mockito.when(${ updatedMockCall.asExprOf[T] }).thenReturn(${ requiredReturnValue }) }
  }

/**
 * Copy of [[https://github.com/mockito/mockito-scala/blob/release/2.x/scalatest/src/main/scala/org/mockito/scalatest/IdiomaticMockito.scala]]
 * mockito-scala doesn't support scala 3 so we have to implement something similar or rewrite to other lib
 */
trait AsyncIdiomaticMockito extends AsyncTestSuite:
  this: Suite =>
  import AsyncIdiomaticMockito.*

  extension[T] (inline mockCall: T)
    transparent inline infix def returns(requiredReturnValue: T): OngoingStubbing[T] =
      ${ returnsMacro[T]('mockCall, 'requiredReturnValue) }

  extension[T] (ongoingStubbing: OngoingStubbing[T])
    def thenAnswer(f: => T): OngoingStubbing[T] =
      ongoingStubbing.thenAnswer((invocation: InvocationOnMock) => f)

  abstract override def withFixture(test: NoArgAsyncTest): FutureOutcome =
    val session = Mockito
      .mockitoSession()
      .initMocks(this)
      .strictness(Strictness.STRICT_STUBS)
      .startMocking()

    val result =
      try super.withFixture(test)
      catch {
        case t: Throwable =>
          session.finishMocking(t)
          throw t
      }

    result.onOutcomeThen(o => session.finishMocking())

  def mock[T <: AnyRef](implicit classTag: ClassTag[T]): T =
    val s = new MockSettingsImpl[T]
      .defaultAnswer(Mockito.RETURNS_DEFAULTS)
      .strictness(org.mockito.quality.Strictness.STRICT_STUBS)

    MockitoSugar.mock[T](s)(using classTag)

  def *[T]: T =
    ArgumentMatchers.any[T]()
    null.asInstanceOf[T]
