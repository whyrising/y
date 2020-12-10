object Libs {
    const val kotlinVersion = "1.4.20"
    const val kotlinApiVersion = "1.4"
    const val jvmTargetVersion = "1.8"

    object Kotest {
        private const val version = "4.3.1"
        const val assertions = "io.kotest:kotest-assertions-core:$version"
        const val runner = "io.kotest:kotest-runner-junit5-jvm:$version"
        const val propertyTest = "io.kotest:kotest-property-jvm:$version"
    }

    object Kotlinx {
        private const val gr = "org.jetbrains.kotlinx"

        private const val atomicfuVersion = "0.14.4"
        private const val serialVersion = "1.0.1"
        private const val coroutinesVersion = "1.4.1"

        const val atomicfu = "$gr:atomicfu:$atomicfuVersion"
        const val serialJson = "$gr:kotlinx-serialization-json:$serialVersion"
        const val coroutines = "$gr:kotlinx-coroutines-core:$coroutinesVersion"
    }
}
