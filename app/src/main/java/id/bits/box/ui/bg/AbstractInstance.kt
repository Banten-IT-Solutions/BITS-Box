package id.bits.box.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}