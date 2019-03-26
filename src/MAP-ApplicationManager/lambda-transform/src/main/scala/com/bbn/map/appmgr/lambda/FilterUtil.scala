package com.bbn.map.appmgr.lambda

object FilterUtil {
  val or = (left: Object, right: Object) => if (left != null) left else right
}