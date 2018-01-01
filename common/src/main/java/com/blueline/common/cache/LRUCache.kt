package com.blueline.common.cache

import kotlin.system.measureTimeMillis


class LRUCache<K,V> (private val initialCapacity:Int): LinkedHashMap<K, V> (initialCapacity,0.75F,true){

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean= this.size > initialCapacity

    @Synchronized
    override fun put(key: K, value: V): V? {
        return super.put(key, value)
    }

    @Synchronized
    override fun get(key: K): V? {
        return super.get(key)
    }

    @Synchronized
    override fun remove(key: K): V? {
        return super.remove(key)
    }

    @Synchronized
    override fun remove(key: K, value: V): Boolean {
        return super.remove(key, value)
    }

    @Synchronized
    override fun getOrDefault(key: K, defaultValue: V): V {
        return super.getOrDefault(key, defaultValue)
    }

}

fun main(args: Array<String>) {
    val cache=LRUCache<Int,String>(10)

   println( measureTimeMillis {
        for(i in 1..100000000){

            cache.put(i,i.toString());
            cache.getOrDefault(i-4,"")
        }
    })



}