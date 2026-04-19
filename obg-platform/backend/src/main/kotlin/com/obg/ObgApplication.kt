package com.obg

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ObgApplication

fun main(args: Array<String>) {
    runApplication<ObgApplication>(*args)
}
