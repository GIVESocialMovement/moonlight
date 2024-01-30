package givers.moonlight.scheduled.quartz.macros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

sealed trait ValidCronExpression {
  def stringExpression: String
}

/**
 * cron string compile time validator based on Quartz
 */
object QuartzCronExpression {
  case class ValidCronExpressionImpl(stringExpression: String) extends ValidCronExpression

  def validate(
    c: blackbox.Context
  )(cronExpr: c.Expr[String]): c.Expr[ValidCronExpression] = {
    import c.universe._

    val str = cronExpr.tree match {
      case Literal(Constant(str: String)) => str
      case other =>
        scala.sys.error(s"Expected some string, got variable ($other)")
    }

    // building cron expression that will fail in case of invalid string
    new org.quartz.CronExpression(str)

    c.Expr(q"""new givers.moonlight.scheduled.quartz.macros.QuartzCronExpression.ValidCronExpressionImpl(
      $cronExpr
    )""")
  }

  def cronExpression(cronExpr: String): ValidCronExpression = macro validate
}
