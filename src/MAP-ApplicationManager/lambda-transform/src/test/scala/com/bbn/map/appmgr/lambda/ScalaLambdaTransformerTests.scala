package com.bbn.map.appmgr.lambda

import org.junit.Assert._
import org.junit.Test
import com.typesafe.scalalogging.LazyLogging
import java.util.UUID
import java.util.Date
import java.security.SecureRandom

class ScalaLambdaTransformerTests extends LazyLogging {
  
  private val slt = new ScalaLambdaTransformer
  private val gen = new SecureRandom
  
  @Test
  def positive {
    assertNotNull(slt.apply(5.0, "v"))
  }
  
  @Test
  def multiplyLambdaTest {
    
    val multiply = (v: Double) => 3 * v

    // works as a function?
    assertTrue(multiply(3) == 9.0d)
    
    // works as a lambda?
    assertTrue(slt.apply(3, "3 * v") == 9.0d)
  }
}