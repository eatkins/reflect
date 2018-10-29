package com.swoval.reflect.thunk

object Foo {
  val two = com.swoval.reflect.Thunk(com.swoval.reflect.Bar.add(1, 1))
}
