package com.bbn.map.appmgr.lambda

/*
 * @author jmcettrick
 * 
 * trait for lambda-based filter plugins
 * 
 */
trait LambdaTransformer {
  
  // prepare a lambda
  def prepare(filterExpression: String): Holder[Function1[Double, Double]] 
  
  // apply a lambda
  def apply(v: Double, filterExpression: String): Double
  
  // setter for object names set from Java code
  def setObjectNames(filterObjectNames: java.util.Set[String])
}