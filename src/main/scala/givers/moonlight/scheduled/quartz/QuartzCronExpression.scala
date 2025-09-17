package givers.moonlight.scheduled.quartz

import scala.quoted.{Expr, Quotes}

sealed trait ValidCronExpression {
  def stringExpression: String
}

/**
 * cron string compile time validator based on Quartz
 */
object QuartzCronExpression {
  case class ValidCronExpressionImpl(stringExpression: String) extends ValidCronExpression

  def validate(cronExpr: Expr[String])(using Quotes): Expr[ValidCronExpression] =
    // building cron expression that will fail in case of invalid string
    new org.quartz.CronExpression(cronExpr.valueOrAbort)

    '{ new givers.moonlight.scheduled.quartz.QuartzCronExpression.ValidCronExpressionImpl(${cronExpr})}

  inline def cronExpression(cronExpr: String): ValidCronExpression = ${validate('cronExpr)}
}
