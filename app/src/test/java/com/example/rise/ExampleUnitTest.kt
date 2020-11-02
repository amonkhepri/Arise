package com.example.rise

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

/*
    @Test
    fun romanToInt(s: String) {

        var sum: Int = 0

        for(i in 0..s.length-1){
            if(i == s.length-1){
                sum += getIntValue(s[i])
            }else if(getIntValue(s[i]) < getIntValue(s[i+1])){
                sum -= getIntValue(s[i])
            }else{
                sum += getIntValue(s[i])
            }
        }

        return sum
    }
*/

    private fun getIntValue(input: Char): Int{
        when(input){
            'I' -> return 1
            'V' -> return 5
            'X' -> return 10
            'L' -> return 50
            'C' -> return 100
            'D' -> return 500
            'M' -> return 1000
            else -> return 0
        }
    }
}
