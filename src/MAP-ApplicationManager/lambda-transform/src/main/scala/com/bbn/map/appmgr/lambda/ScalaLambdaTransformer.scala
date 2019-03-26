package com.bbn.map.appmgr.lambda

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.Set
import scala.tools.nsc.GenericRunnerSettings
import scala.tools.nsc.interpreter.IMain
import com.typesafe.scalalogging.LazyLogging

import collection.JavaConverters._

@org.springframework.stereotype.Component
class ScalaLambdaTransformer extends LambdaTransformer with LazyLogging {

  private val interpreter = new ThreadLocal[IMain]
  private val stringLambdaMap = new ConcurrentHashMap[String, Holder[Function1[Double, Double]]]
  
  // scala mutable set
  private var objectNames: Set[String] = Set()
  
  override def setObjectNames(filterObjectNames: java.util.Set[String]) = { 
    this.objectNames = filterObjectNames.asScala
  }
  
  override def apply(v: Double, filterExpression: String): Double = {
    
    logger.trace("transform value " + v + " according to expression " + filterExpression)
   
    // apply the lambda
    prepare(filterExpression).content(v)
  }
  
  // interpret and pre-cache a filter lambda
  override def prepare(filterExpression: String): Holder[Function1[Double, Double]] = {
    
    // lazily init thread-local with interpreter
    if (interpreter.get == null) {
      interpreter.set(initInterpreter)
    }
    
    logger.trace("prepare lambda filter: " + filterExpression)
    
    var fch = stringLambdaMap.get(filterExpression)
    
    if (fch == null) {
      
      logger.trace("lambda cache miss for " + filterExpression)
    
      fch = new Holder[Function1[Double, Double]](null) // pass in a lambda holder object of the appropriate type, with null content
     
      interpreter.get.bind("fch", "com.bbn.map.appmgr.lambda.Holder[Function1[Double, Double]]", fch)
    
      val importText = ((for (filterObjectName <- objectNames) yield "import " + filterObjectName + "._") + "import com.bbn.map.appmgr.lambda.FilterUtil._").mkString("\n")

      logger.trace("functional object import statement:\n" + importText + "\n")
      
      val totalText: String = importText + "\n" + "fch.content = (v: Double) => " + filterExpression
    
      logger.trace("code to interpret:\n" + totalText + "\n")
    
      val resultFlag = interpreter.get.interpret(totalText)
    
      stringLambdaMap.put(filterExpression, fch)
    
    } else {
      logger.trace("lambda cache hit for " + filterExpression)
    }
    
    fch
  }
  
  private def initInterpreter: IMain = {
    val settings = new GenericRunnerSettings(println _)
    settings.usejavacp.value = true
//    settings.classpath.value = "/Users/jmcettri/scala/libs/scala-library-2.11.11.jar:/Users/jmcettri/scala/libs/scala-compiler-2.11.11.jar:/Users/jmcettri/scala/libs/scala-reflect-2.11.11.jar:/Users/jmcettri/.m2/repository/com/bbn/map/lambda-transform/0.0.2-DEV/lambda-transform-0.0.2-DEV.jar"
//    settings.bootclasspath append "/Users/jmcettri/scala/libs/scala-library-2.11.11.jar:/Users/jmcettri/scala/libs/scala-compiler-2.11.11.jar:/Users/jmcettri/scala/libs/scala-reflect-2.11.11.jar:/Users/jmcettri/.m2/repository/com/bbn/map/lambda-transform/0.0.2-DEV/lambda-transform-0.0.2-DEV.jar"
    
    val flusher = new java.io.PrintWriter(System.out)
    new IMain(settings, flusher)        
  }

}