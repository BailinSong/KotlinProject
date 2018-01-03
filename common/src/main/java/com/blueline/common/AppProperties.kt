@file:Suppress("KDocUnresolvedReference")

package com.blueline.common

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.regex.Pattern


/**
 *  应用属性
 *
 *  属性读取优先级
 *  	命令行属性>系统属性>环境变量>应用当前目录属性文件>资源路径属性文件
 *  	高优先级属性覆盖低优先级属性
 *
 *  多环境配置 java -jar xxx.jar --profiles.active=<ActiveName>.
 * 		启用后对应属性文件:application[-<ActiveName>].properties
 * 		不指定环境配置时可以没有application.properties属性文件
 *
 *  命令行属性 java -jar xxx.jar --<PropertyName>=[PropertyValue]...
 *  	String属性"--" 开头： --<PropertyName>=[PropertyValue]
 *  	boolean属性：--<PropertyName> 读取时值为true
 *
 *  系统属性 System.getProperties()
 *  	java自动获取系统属性
 *  	命令行赋值String属性-D<PropertyName>=[PropertyValue]
 *
 *  环境变量 System.getenv()
 *  	系统及环境变量
 *
 *  应用当前目录属性文件
 *  	对应路径 System.getProperty("user.dir") 或 java -jar xxx.jar --profiles.path=<profiles.path>
 *  	如果指定了profiles.active 那么对应属性文件必须存在
 *  	属性文件名称：application[-<profiles.active>].properties
 *
 *  资源路径属性文件
 *  	对应路径 Properties.class.getResource("/")
 *  	如果指定了profiles.active 并且 application.properties 存在那么对应属性文件必须存在
 *  	属性文件名称：application[-<profiles.active>].properties
 *
 *  支持属性中引用直接引用属性 ${PropertyName},运行期间会替换为属性值
 *  	<PropertyNameX>=PropertyValueX
 *  	<PropertyNameY>=${PropertyNameX}-ok  解析后：<PropertyNameY>=PropertyValueX-ok
 *
 *  支持自动属性随机值
 *  	<PropertyName>=${random.value} 32位随机字符串:415a29f9e5dd38e23f2d4fd39e79a821
 *      <PropertyName>=${random.uuid} uuid随机字符串:505a3dd5-7742-4a51-bac9-e14291582e49
 *  	<PropertyName>=${random.long} 随机long值正数:0-MaxLongValue
 *  	<PropertyName>=${random.int} 随机int值正数:0-MaxIntValue
 *  	<PropertyName>=${random.int[MaxBound>]} 随机int值正数:0-MaxBound
 *  	<PropertyName>=${random.int[<MiniBound>,<MaxBound>]} 随机int值正数:MiniBound-MaxBound
 */


object AppProperties {

    private val PROPERTY_PROFILES_ACTIVE = "profiles.active"
    private val PROPERTY_PROFILES_PATH = "profiles.path"
    private var PROFILES_ACTIVE: String? = null

    private val VALUE_FUN = mapOf(
            "random" to mapOf(
                    "value" to Randoms.VALUE,
                    "int" to Randoms.INT,
                    "long" to Randoms.LONG,
                    "uuid" to Randoms.UUID
            )
    )

    private val CMD_PARAMS = paresCmdParamsToProperties()
    private val SYS_PROPERTIES = getProperties(System.getProperties())
    private val SYS_ENV = getProperties(System.getenv())
    private val APPLICATION_PROPERTIES = getApplicationProperties(CMD_PARAMS.getProperty(PROPERTY_PROFILES_PATH, ""))
    private val CLASS_PROPERTIES = getClassPathProperties()

    /**
     * attribute priority
     *      CMD > SYS > ENV > APPLICATION > CLASS
     *  parameter esolution priority
     *      DynamicValues > Variable
     */
    val FINAL_PROPERTIES: Properties = with(Properties()) {
        mergeProperties(CLASS_PROPERTIES)
        mergeProperties(APPLICATION_PROPERTIES)
        mergeProperties(SYS_ENV)
        mergeProperties(SYS_PROPERTIES)
        mergeProperties(CMD_PARAMS)
        generatingDynamicValues()
        analyticVariable()
    }

    private fun getClassPathProperties(): Properties {
        val properties = Properties()
        var inputStream: InputStream? = null
        try {
            inputStream = Properties::class.java.getResourceAsStream("/application.properties")
            properties.load(inputStream)
        } catch (e: Exception) {
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (PROFILES_ACTIVE == null) {
            PROFILES_ACTIVE = properties.getProperty(PROPERTY_PROFILES_ACTIVE, null)
        }
        if (PROFILES_ACTIVE == null) {
            return properties
        } else {
            try {
                inputStream = Properties::class.java.getResourceAsStream("/application-$PROFILES_ACTIVE.properties")
                val tempProperties=Properties()
                tempProperties.load(inputStream)
                properties.mergeProperties(tempProperties)
            } catch (e: Exception) {
            } finally {
                try {
                    inputStream!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return properties
        }
    }

    private fun getApplicationProperties(path: String): Properties {

        var propertiesPath: String? = path
        if (propertiesPath == null || propertiesPath.isEmpty()) {
            propertiesPath = System.getProperty("user.dir")
        }

        val properties = Properties()
        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(propertiesPath + File.separatorChar + "application.properties")
            properties.load(inputStream)
        } catch (e: Exception) {
        } finally {
            try {
                inputStream!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        if (PROFILES_ACTIVE == null) {
            PROFILES_ACTIVE = properties.getProperty(PROPERTY_PROFILES_ACTIVE, null)
        }
        if (PROFILES_ACTIVE == null) {
            return properties
        } else {
            try {
                inputStream = FileInputStream("$propertiesPath/application-$PROFILES_ACTIVE.properties")
                val tempProperties=Properties()
                tempProperties.load(inputStream)
                properties.mergeProperties(tempProperties)
            } catch (e: Exception) {
                throw RuntimeException("$propertiesPath/application-$PROFILES_ACTIVE.properties Exception.", e)
            } finally {
                try {
                    inputStream!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return properties
        }
    }

    private fun paresCmdParamsToProperties(): Properties {
        val commandString = System.getProperty("sun.java.command")
        val properties = Properties()
        if (commandString == null || commandString.isEmpty()) {
            return properties
        }
        val p = Pattern.compile("""/\s*(".+?"|[^:\s])+((\s*:\s*(".+?"|[^\s])+)|)|(".+?"|[^"\s])+""")
        val matcher = p.matcher(commandString)
        var paramStr: String
        while (matcher.find()) {
            paramStr = matcher.group()
            if (paramStr.startsWith("--")) {
                val key: String
                var value: String
                paramStr = paramStr.substring(2)
                val valueIndex = paramStr.indexOf('=')
                if (valueIndex < 0) {
                    key = paramStr
                    value = "true"
                } else {

                    key = paramStr.substring(0, valueIndex)
                    value = paramStr.substring(valueIndex + 1)

                }
                if (value.startsWith("\"")) value = value.substring(1)
                if (value.endsWith("\"")) value = value.substring(0, value.length - 1)
                properties.setProperty(key, value)
            }
        }
        if (PROFILES_ACTIVE == null) {
            PROFILES_ACTIVE = properties.getProperty(PROPERTY_PROFILES_ACTIVE, null)
        }
        return properties
    }

    private fun Properties.analyticVariable(prop: Properties = this): Properties {
        prop.keys.forEach { src ->
            prop.keys.forEach { dest ->
                prop.setProperty(dest as String, prop.getProperty(dest).replace("\${$src}", prop.getProperty(src as String, "")))
            }
        }

        return prop
    }

    private fun Properties.generatingDynamicValues(prop: Properties = this): Properties {

        prop.keys.forEach { it ->
            var value = prop.getProperty(it as String, "")
            val p = Pattern.compile("""\$\{[a-zA-Z0-9\.\,\(\)\[\]\ ]*\}""")
            val matcher = p.matcher(value)
            while (matcher.find()) {
                val temp = matcher.group()
                var paramInfo = arrayOfNulls<String>(0)
                val claInfo = temp.substring(2, temp.length - 1).split("""\.""".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                if (claInfo.size < 2) continue
                val cla = claInfo[0]
                val funInfo = claInfo[1].split("""[\(\[]""".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                if (claInfo.isEmpty()) continue
                val method = funInfo[0]
                if (funInfo.size > 1 && !funInfo[1].isEmpty())
                    paramInfo = funInfo[1].split("""[\,\]\)]""".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                val claM = VALUE_FUN[cla] ?: continue
                val funM = claM[method] ?: continue
                value = value.replace(
                        temp,
                        funM.compute(paramInfo))
            }
            prop.setProperty(it, value)

        }
        return prop
    }

    private fun Properties.mergeProperties(src: Properties, dest: Properties = this): Properties {
        src.forEach { k, v -> dest.setProperty(k as String, v as String) }
        return dest
    }

    private fun getProperties(properties: Properties): Properties {

        val temp = properties.clone() as Properties

        if (PROFILES_ACTIVE == null) {
            PROFILES_ACTIVE = temp.getProperty(PROPERTY_PROFILES_ACTIVE, null)
        }
        return temp
    }

    private fun getProperties(map: Map<String, String>): Properties {

        val properties = Properties()
        map.forEach { k, v -> properties.setProperty(k, v) }

        if (PROFILES_ACTIVE == null) {
            PROFILES_ACTIVE = properties.getProperty(PROPERTY_PROFILES_ACTIVE, null)
        }
        return properties
    }


    /**
     * 获取属性值
     * @param propertyName 属性名
     * @param defaultValue 当值不存在时返回此默认值，默认值不能为null
     * @param <V> 默认值类型
     * @return 根据默认值类型转换并返回属性名对应的值
    </V> */
    inline operator fun <reified V> get(propertyName: String, defaultValue: V): V {

        val linuxPropertyName = propertyName.replace('.', '_')
        var value = FINAL_PROPERTIES.getProperty(propertyName, null)
        value = if (value == null) FINAL_PROPERTIES.getProperty(linuxPropertyName, null) else value
        return if (value != null) {
            covertValue(value)
        } else {
            defaultValue
        }

    }

    inline operator fun <reified V> get(propertyName: String): V? {

        val linuxPropertyName = propertyName.replace('.', '_')
        var value = FINAL_PROPERTIES.getProperty(propertyName, null)
        value = if (value == null) FINAL_PROPERTIES.getProperty(linuxPropertyName, null) else value
        return if (value != null) {
            covertValue(value)
        } else {
            null
        }

    }

      /**
     * 设置应用内属性值，如果属性已存在将会被覆盖
     * @param propertyName 属性名
     * @param propertyValue 属性值
     * @return 属性名源值
     */
    @Synchronized operator fun set(propertyName: String, propertyValue: String): String {
        FINAL_PROPERTIES.setProperty(propertyName, propertyValue)
        return System.setProperty(propertyName, propertyValue)
    }


    @Suppress("IMPLICIT_CAST_TO_ANY")
    @JvmName("=covertValue") inline fun <reified V> covertValue(value: String): V {
        return when (V::class.java) {
                java.lang.String::class.java -> value.trim()
                java.lang.Boolean::class.java -> java.lang.Boolean.valueOf(value.trim())
                java.lang.Byte::class.java -> java.lang.Byte.valueOf(value.trim())
                java.lang.Short::class.java -> java.lang.Short.valueOf(value.trim())
                java.lang.Integer::class.java -> java.lang.Integer.valueOf(value.trim())
                java.lang.Long::class.java -> java.lang.Long.valueOf(value.trim())
                java.lang.Float::class.java -> java.lang.Float.valueOf(value.trim())
                java.lang.Double::class.java -> java.lang.Double.valueOf(value.trim())
                java.lang.Character::class.java -> java.lang.Character.valueOf(value[0])
                else -> value
            } as V

    }

    private fun printProperties(pro: Properties) {

        pro.forEach { k, v -> println("$k\t=\t$v") }

    }

    @Suppress("unused")
    fun printProperties() {
        printProperties(FINAL_PROPERTIES)
    }

    internal interface ICalculate {
        fun compute(params: Array<String?>): String
    }

    internal class Randoms {
        init {
            throw RuntimeException("This operation is not supported.")
        }

        companion object {


            val VALUE: ICalculate = object : ICalculate {
                override fun compute(params: Array<String?>): String {
                    var random = String.format("%d%010d",
                            System.currentTimeMillis(), Math.abs(Random().nextLong()))
                    try {

                        val messageDigest = MessageDigest.getInstance("md5")
                        val digest = messageDigest.digest(random.toByteArray())
                        random = printHexString(digest)
                    } catch (e: NoSuchAlgorithmException) {
                        //                e.printStackTrace();
                    }

                    return random
                }

                private fun printHexString(b: ByteArray): String {
                    var a = ""
                    for (i in b.indices) {
                        var hex = Integer.toHexString(b[i].toInt())
                        if (hex.length == 1) {
                            hex = '0' + hex
                        }
                        a += hex
                    }
                    return a
                }
            }

            val INT: ICalculate = object : ICalculate {
                override fun compute(params: Array<String?>): String {
                    return when (params.size) {
                        1 -> Random().nextInt(Integer.valueOf(params[0])).toString()
                        2 -> (Random().nextInt(Integer.valueOf(params[1]) - Integer.valueOf(params[0])) + Integer.valueOf(params[0])).toString()
                        else -> Math.abs(Random().nextInt()).toString()
                    }
                }
            }

            val LONG: ICalculate = object : ICalculate {
                override fun compute(params: Array<String?>): String {
                    return Math.abs(Random().nextLong()).toString()
                }
            }

            val UUID: ICalculate = object : ICalculate {
                override fun compute(params: Array<String?>): String {
                    return java.util.UUID.randomUUID().toString()
                }
            }
        }


    }
}

typealias PROPS = AppProperties

fun main(args: Array<String>) {

    println(PROPS["abc", ""])
    val amChar:Char?=PROPS["amChara"]
    amChar?.let {
        println("amChar=$amChar")
    }
    println("${PROPS["USERNAME", 0]}")
    println(PROPS["java.awt.graphicsenv", ""])
}