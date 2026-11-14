package com.clony

/** A selectable device profile in the clone build-props editor. */
internal data class CloneBuildProfile(
    val name: String,
    val summary: String,
    val props: CloneBuildProps
)

private data class DeviceBuildSpec(
    val name: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val product: String,
    val device: String,
    val board: String,
    val release: String = "14",
    val sdkInt: Int = 34
)

private fun DeviceBuildSpec.toProfile(index: Int): CloneBuildProfile {
    val build = when {
        sdkInt >= 37 -> "CP2A.260101.${index.toString().padStart(3, '0')}"
        sdkInt >= 36 -> "BP2A.250605.${index.toString().padStart(3, '0')}"
        sdkInt >= 35 -> "AP3A.250101.${index.toString().padStart(3, '0')}"
        sdkInt >= 34 -> "AP2A.240905.${index.toString().padStart(3, '0')}"
        sdkInt >= 33 -> "TQ3A.230805.${index.toString().padStart(3, '0')}"
        sdkInt >= 31 -> "SP1A.210812.${index.toString().padStart(3, '0')}"
        sdkInt >= 30 -> "RQ3A.200805.${index.toString().padStart(3, '0')}"
        sdkInt >= 29 -> "QP1A.190711.${index.toString().padStart(3, '0')}"
        sdkInt >= 28 -> "PPR1.181005.${index.toString().padStart(3, '0')}"
        else -> "NMF26X.${index.toString().padStart(3, '0')}"
    }
    val props = CloneBuildProps(
        deviceName = name,
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        product = product,
        device = device,
        board = board,
        radio = "TEST-RADIO-$device-$sdkInt",
        bootloader = "TEST-BOOTLOADER-$model",
        fingerprint = "$brand/$product/$device:$release/$build/$build:user/release-keys",
        build = build,
        host = "clony-test-build-host",
        time = (1735689600000L + index * 86_400_000L).toString(),
        osVersion = when {
            sdkInt >= 37 -> "6.12.${120 + index}-android$release-test"
            sdkInt >= 36 -> "6.6.${120 + index}-android$release-test"
            sdkInt <= 28 -> "4.4.78-android$release-test"
            else -> "5.15.${120 + index}-android$release-test"
        },
        release = release,
        sdkInt = sdkInt.toString()
    )
    return CloneBuildProfile(name, "$model · Android $release test build", props)
}

private val testDeviceBuildSpecs = listOf(
    DeviceBuildSpec("Samsung Galaxy S24 Ultra", "samsung", "samsung", "SM-S928B", "e3qxxx", "e3q", "pineapple", "15", 35),
    DeviceBuildSpec("Samsung Galaxy S23 Ultra", "samsung", "samsung", "SM-S918B", "dm3qxxx", "dm3q", "kalama", "14", 34),
    DeviceBuildSpec("Samsung Galaxy A55 5G", "samsung", "samsung", "SM-A556B", "a55x", "a55x", "s5e8845", "14", 34),
    DeviceBuildSpec("Google Pixel 9 Pro XL", "Google", "google", "GZC4K", "komodo", "komodo", "zuma", "15", 35),
    DeviceBuildSpec("Google Pixel 8 Pro", "Google", "google", "GC3VE", "husky", "husky", "gs201", "15", 35),
    DeviceBuildSpec("Google Pixel 7 Pro", "Google", "google", "GE2AE", "cheetah", "cheetah", "gs201", "14", 34),
    DeviceBuildSpec("OnePlus 12", "OnePlus", "OnePlus", "CPH2573", "waffle", "waffle", "pineapple", "15", 35),
    DeviceBuildSpec("OnePlus 11", "OnePlus", "OnePlus", "CPH2449", "salami", "salami", "taro", "14", 34),
    DeviceBuildSpec("Xiaomi 14", "Xiaomi", "Xiaomi", "23127PN0CG", "houji", "houji", "pineapple", "14", 34),
    DeviceBuildSpec("Xiaomi 13", "Xiaomi", "Xiaomi", "2211133G", "fuxi", "fuxi", "taro", "14", 34),
    DeviceBuildSpec("Redmi Note 13 Pro 5G", "Xiaomi", "Redmi", "2312DRA50G", "garnet", "garnet", "pineapple", "14", 34),
    DeviceBuildSpec("Motorola Edge 50 Pro", "Motorola", "motorola", "XT2403-4", "manaus", "manaus", "sm8635", "14", 34),
    DeviceBuildSpec("Motorola Razr 50 Ultra", "Motorola", "motorola", "XT2451-3", "glory", "glory", "sm8635", "14", 34),
    DeviceBuildSpec("Sony Xperia 1 VI", "Sony", "Sony", "XQ-EC54", "pdx245", "pdx245", "pineapple", "14", 34),
    DeviceBuildSpec("Nothing Phone (2)", "Nothing", "Nothing", "A065", "pong", "pong", "taro", "14", 34),
    DeviceBuildSpec("Nothing Phone (2a)", "Nothing", "Nothing", "A142", "pacman", "pacman", "mt6877", "14", 34),
    DeviceBuildSpec("ASUS ROG Phone 8 Pro", "asus", "asus", "AI2401_PRO", "AI2401", "AI2401", "pineapple", "14", 34),
    DeviceBuildSpec("OPPO Find X7 Ultra", "OPPO", "OPPO", "PHZ110", "PJF110", "PJF110", "pineapple", "14", 34),
    DeviceBuildSpec("vivo X100 Pro", "vivo", "vivo", "V2324A", "PA2324", "PA2324", "mt6989", "14", 34),
    DeviceBuildSpec("realme GT 6", "realme", "realme", "RMX3800", "RMX3800", "RMX3800", "pineapple", "14", 34),
    DeviceBuildSpec("Samsung Galaxy S24+", "samsung", "samsung", "SM-S926B", "e2qxxx", "e2q", "pineapple", "15", 35),
    DeviceBuildSpec("Samsung Galaxy S24", "samsung", "samsung", "SM-S921B", "e1qxxx", "e1q", "pineapple", "15", 35),
    DeviceBuildSpec("Samsung Galaxy Z Fold6", "samsung", "samsung", "SM-F956B", "q6qxxx", "q6q", "pineapple", "15", 35),
    DeviceBuildSpec("Samsung Galaxy Z Flip6", "samsung", "samsung", "SM-F741B", "b6qxxx", "b6q", "pineapple", "15", 35),
    DeviceBuildSpec("Samsung Galaxy S23+", "samsung", "samsung", "SM-S916B", "dm2qxxx", "dm2q", "kalama"),
    DeviceBuildSpec("Samsung Galaxy S23", "samsung", "samsung", "SM-S911B", "dm1qxxx", "dm1q", "kalama"),
    DeviceBuildSpec("Samsung Galaxy Z Fold5", "samsung", "samsung", "SM-F946B", "q5qxxx", "q5q", "kalama"),
    DeviceBuildSpec("Samsung Galaxy Z Flip5", "samsung", "samsung", "SM-F731B", "b5qxxx", "b5q", "kalama"),
    DeviceBuildSpec("Samsung Galaxy S22 Ultra", "samsung", "samsung", "SM-S908B", "b0qxxx", "b0q", "sm8450", "13", 33),
    DeviceBuildSpec("Samsung Galaxy S22", "samsung", "samsung", "SM-S901B", "r0qxxx", "r0q", "sm8450", "13", 33),
    DeviceBuildSpec("Samsung Galaxy A54 5G", "samsung", "samsung", "SM-A546B", "a54x", "a54x", "s5e8835"),
    DeviceBuildSpec("Samsung Galaxy A35 5G", "samsung", "samsung", "SM-A356B", "a35x", "a35x", "s5e8535"),
    DeviceBuildSpec("Google Pixel 9 Pro", "Google", "google", "GEC77", "caiman", "caiman", "zuma", "15", 35),
    DeviceBuildSpec("Google Pixel 9", "Google", "google", "G2YBB", "tokay", "tokay", "zuma", "15", 35),
    DeviceBuildSpec("Google Pixel 8", "Google", "google", "GKWS6", "shiba", "shiba", "gs201", "15", 35),
    DeviceBuildSpec("Google Pixel 8a", "Google", "google", "G8HHN", "akita", "akita", "gs201", "15", 35),
    DeviceBuildSpec("Google Pixel 7", "Google", "google", "GVU6C", "panther", "panther", "gs201"),
    DeviceBuildSpec("Google Pixel 7a", "Google", "google", "GHL1X", "lynx", "lynx", "gs201"),
    DeviceBuildSpec("Google Pixel 6 Pro", "Google", "google", "GLUOG", "raven", "raven", "gs101", "13", 33),
    DeviceBuildSpec("Google Pixel 6a", "Google", "google", "GX7AS", "bluejay", "bluejay", "gs101", "13", 33),
    DeviceBuildSpec("OnePlus 13", "OnePlus", "OnePlus", "CPH2653", "dodge", "dodge", "sun", "15", 35),
    DeviceBuildSpec("OnePlus 12R", "OnePlus", "OnePlus", "CPH2609", "aston", "aston", "kalama", "15", 35),
    DeviceBuildSpec("OnePlus 10 Pro", "OnePlus", "OnePlus", "NE2213", "negroni", "negroni", "taro"),
    DeviceBuildSpec("OnePlus 10T", "OnePlus", "OnePlus", "CPH2413", "ovaltine", "ovaltine", "taro"),
    DeviceBuildSpec("OnePlus Nord 4", "OnePlus", "OnePlus", "CPH2663", "avalon", "avalon", "sm8635", "15", 35),
    DeviceBuildSpec("OnePlus Nord 3", "OnePlus", "OnePlus", "CPH2493", "larry", "larry", "mt6983"),
    DeviceBuildSpec("OnePlus Nord CE4", "OnePlus", "OnePlus", "CPH2613", "benz", "benz", "sm7500"),
    DeviceBuildSpec("OnePlus Nord CE 3 Lite", "OnePlus", "OnePlus", "CPH2467", "larrylite", "larrylite", "sm6375", "13", 33),
    DeviceBuildSpec("Xiaomi 14 Ultra", "Xiaomi", "Xiaomi", "24030PN60G", "aurora", "aurora", "pineapple", "14", 34),
    DeviceBuildSpec("Xiaomi 14T Pro", "Xiaomi", "Xiaomi", "2407FPN8EG", "rothko", "rothko", "mt6989", "14", 34),
    DeviceBuildSpec("Xiaomi 14T", "Xiaomi", "Xiaomi", "2406APNFAG", "degas", "degas", "mt6897", "14", 34),
    DeviceBuildSpec("Xiaomi 13T Pro", "Xiaomi", "Xiaomi", "23078PND5G", "corot", "corot", "mt6985", "14", 34),
    DeviceBuildSpec("Xiaomi 13T", "Xiaomi", "Xiaomi", "2306EPN60G", "aristotle", "aristotle", "mt6896", "14", 34),
    DeviceBuildSpec("Xiaomi 12 Pro", "Xiaomi", "Xiaomi", "2201122G", "zeus", "zeus", "sm8450", "13", 33),
    DeviceBuildSpec("Xiaomi 12T Pro", "Xiaomi", "Xiaomi", "22081212UG", "diting", "diting", "sm8475", "13", 33),
    DeviceBuildSpec("Redmi Note 13 Pro+ 5G", "Xiaomi", "Redmi", "23090RA98G", "zircon", "zircon", "mt6877", "14", 34),
    DeviceBuildSpec("Redmi Note 12 Pro 5G", "Xiaomi", "Redmi", "22101316G", "ruby", "ruby", "mt6877", "13", 33),
    DeviceBuildSpec("Redmi Note 12", "Xiaomi", "Redmi", "23021RAAEG", "tapas", "tapas", "sm4375", "13", 33),
    DeviceBuildSpec("POCO F6", "Xiaomi", "POCO", "24069PC21G", "peridot", "peridot", "sm8635", "14", 34),
    DeviceBuildSpec("POCO F6 Pro", "Xiaomi", "POCO", "23113RKC6G", "vermeer", "vermeer", "sm8550", "14", 34),
    DeviceBuildSpec("POCO X6 Pro", "Xiaomi", "POCO", "2311DRK48G", "duchamp", "duchamp", "mt6897", "14", 34),
    DeviceBuildSpec("POCO X6", "Xiaomi", "POCO", "23122PCD1G", "moonstone", "moonstone", "sm7435", "14", 34),
    DeviceBuildSpec("Motorola Edge 50 Ultra", "Motorola", "motorola", "XT2401-1", "aito", "aito", "sm8650", "14", 34),
    DeviceBuildSpec("Motorola Edge 40 Pro", "Motorola", "motorola", "XT2301-4", "rtwo", "rtwo", "sm8550", "14", 34),
    DeviceBuildSpec("Motorola Edge 40 Neo", "Motorola", "motorola", "XT2307-1", "lyriq", "lyriq", "mt6896", "14", 34),
    DeviceBuildSpec("Motorola Razr 50", "Motorola", "motorola", "XT2453-1", "bloom", "bloom", "sm8635", "14", 34),
    DeviceBuildSpec("Motorola Razr 40 Ultra", "Motorola", "motorola", "XT2321-3", "juno", "juno", "sm8475", "14", 34),
    DeviceBuildSpec("Motorola Moto G84 5G", "Motorola", "motorola", "XT2347-2", "bangkk", "bangkk", "sm6375", "13", 33),
    DeviceBuildSpec("Sony Xperia 1 V", "Sony", "Sony", "XQ-DQ54", "pdx234", "pdx234", "kalama", "14", 34),
    DeviceBuildSpec("Sony Xperia 5 V", "Sony", "Sony", "XQ-DE54", "pdx234", "pdx234", "kalama", "14", 34),
    DeviceBuildSpec("Sony Xperia 10 VI", "Sony", "Sony", "XQ-ES54", "pdx242", "pdx242", "sm6375", "14", 34),
    DeviceBuildSpec("CMF Phone 1", "Nothing", "Nothing", "A015", "tetris", "tetris", "mt6878", "14", 34),
    DeviceBuildSpec("Nothing Phone (3a)", "Nothing", "Nothing", "A059", "asteroids", "asteroids", "sm7635", "15", 35),
    DeviceBuildSpec("ASUS Zenfone 10", "asus", "asus", "AI2302", "AI2302", "AI2302", "sm8550", "14", 34),
    DeviceBuildSpec("ASUS ROG Phone 7", "asus", "asus", "AI2205", "AI2205", "AI2205", "sm8550", "14", 34),
    DeviceBuildSpec("OPPO Find X8 Pro", "OPPO", "OPPO", "CPH2659", "haotian", "haotian", "mt6991", "15", 35),
    DeviceBuildSpec("OPPO Find X8", "OPPO", "OPPO", "CPH2651", "volcano", "volcano", "mt6991", "15", 35),
    DeviceBuildSpec("OPPO Reno12 Pro 5G", "OPPO", "OPPO", "CPH2629", "dali", "dali", "mt6896", "14", 34),
    DeviceBuildSpec("OPPO Reno10 Pro+ 5G", "OPPO", "OPPO", "CPH2521", "oplus", "oplus", "sm8g2", "14", 34),
    DeviceBuildSpec("OPPO Find N3", "OPPO", "OPPO", "CPH2499", "oplus", "oplus", "sm8g2", "14", 34),
    DeviceBuildSpec("vivo X200 Pro", "vivo", "vivo", "V2413", "thorne", "thorne", "mt6989", "15", 35),
    DeviceBuildSpec("vivo X100", "vivo", "vivo", "V2309A", "PA2309", "PA2309", "mt6989", "14", 34),
    DeviceBuildSpec("vivo V30 Pro", "vivo", "vivo", "V2319", "cancunf", "cancunf", "mt6896", "14", 34),
    DeviceBuildSpec("iQOO 12", "vivo", "iQOO", "I2220", "enuma", "enuma", "sm8650", "14", 34),
    DeviceBuildSpec("realme GT 6T", "realme", "realme", "RMX3853", "RMX3853", "RMX3853", "sm8635", "15", 35),
    DeviceBuildSpec("realme GT 5 Pro", "realme", "realme", "RMX3888", "RMX3888", "RMX3888", "sm8650", "14", 34),
    DeviceBuildSpec("realme 12 Pro+ 5G", "realme", "realme", "RMX3840", "RMX3840", "RMX3840", "sm7g2", "14", 34),
    DeviceBuildSpec("realme 11 Pro+ 5G", "realme", "realme", "RMX3741", "RMX3741", "RMX3741", "mt6877", "14", 34),
    DeviceBuildSpec("Huawei Pura 70 Pro", "HUAWEI", "HUAWEI", "HBN-AL00", "HBN", "HBN", "kirin9000s", "12", 31),
    DeviceBuildSpec("Huawei Mate 60 Pro", "HUAWEI", "HUAWEI", "ALN-AL00", "ALN", "ALN", "kirin9000s", "12", 31),
    DeviceBuildSpec("HONOR Magic6 Pro", "HONOR", "HONOR", "BVL-AN00", "BVL", "BVL", "sm8650", "14", 34),
    DeviceBuildSpec("HONOR 200 Pro", "HONOR", "HONOR", "ELP-AN00", "ELP", "ELP", "sm7g3", "14", 34),
    DeviceBuildSpec("nubia RedMagic 9 Pro", "nubia", "nubia", "NX769J", "NX769J", "NX769J", "sm8650", "14", 34),
    DeviceBuildSpec("nubia Z60 Ultra", "nubia", "nubia", "NX721J", "NX721J", "NX721J", "sm8g3", "14", 34),
    DeviceBuildSpec("Infinix GT 20 Pro", "Infinix", "Infinix", "X6871", "X6871", "X6871", "mt6896", "14", 34),
    DeviceBuildSpec("TECNO Camon 30 Pro 5G", "TECNO", "TECNO", "CL8", "CL8", "CL8", "mt6896", "14", 34),
    DeviceBuildSpec("Fairphone 5", "Fairphone", "Fairphone", "FP5", "FP5", "FP5", "qcm6490", "14", 34),
    DeviceBuildSpec("Lenovo Legion Y70", "Lenovo", "Lenovo", "L71091", "L71091", "L71091", "sm8475", "13", 33),
    DeviceBuildSpec("ZTE Axon 50 Ultra", "ZTE", "ZTE", "A2023P", "A2023P", "A2023P", "sm8g2", "13", 33),
    DeviceBuildSpec("Sharp Aquos R8 Pro", "Sharp", "SHARP", "SH-51D", "SH51D", "SH51D", "sm8550", "14", 34),
    DeviceBuildSpec("Samsung Galaxy S24 Ultra", "samsung", "samsung", "SM-S928B", "e3qxxx", "e3q", "pineapple", "16", 36),
    DeviceBuildSpec("Samsung Galaxy S23 Ultra", "samsung", "samsung", "SM-S918B", "dm3qxxx", "dm3q", "kalama", "16", 36),
    DeviceBuildSpec("Google Pixel 9 Pro XL", "Google", "google", "GZC4K", "komodo", "komodo", "zuma", "16", 36),
    DeviceBuildSpec("Google Pixel 8 Pro", "Google", "google", "GC3VE", "husky", "husky", "gs201", "16", 36),
    DeviceBuildSpec("OnePlus 12", "OnePlus", "OnePlus", "CPH2573", "waffle", "waffle", "pineapple", "16", 36),
    DeviceBuildSpec("Xiaomi 14", "Xiaomi", "Xiaomi", "23127PN0CG", "houji", "houji", "pineapple", "16", 36),
    DeviceBuildSpec("Motorola Edge 50 Pro", "Motorola", "motorola", "XT2403-4", "manaus", "manaus", "sm8635", "16", 36),
    DeviceBuildSpec("Nothing Phone (2)", "Nothing", "Nothing", "A065", "pong", "pong", "taro", "16", 36),
    DeviceBuildSpec("ASUS ROG Phone 8 Pro", "asus", "asus", "AI2401_PRO", "AI2401", "AI2401", "pineapple", "16", 36),
    DeviceBuildSpec("OPPO Find X7 Ultra", "OPPO", "OPPO", "PHZ110", "PJF110", "PJF110", "pineapple", "16", 36),
    DeviceBuildSpec("vivo X100 Pro", "vivo", "vivo", "V2324A", "PA2324", "PA2324", "mt6989", "16", 36),
    DeviceBuildSpec("Samsung Galaxy S24 Ultra", "samsung", "samsung", "SM-S928B", "e3qxxx", "e3q", "pineapple", "17", 37),
    DeviceBuildSpec("Samsung Galaxy S23 Ultra", "samsung", "samsung", "SM-S918B", "dm3qxxx", "dm3q", "kalama", "17", 37),
    DeviceBuildSpec("Google Pixel 9 Pro XL", "Google", "google", "GZC4K", "komodo", "komodo", "zuma", "17", 37),
    DeviceBuildSpec("Google Pixel 8 Pro", "Google", "google", "GC3VE", "husky", "husky", "gs201", "17", 37),
    DeviceBuildSpec("OnePlus 13", "OnePlus", "OnePlus", "CPH2653", "dodge", "dodge", "sun", "17", 37),
    DeviceBuildSpec("Xiaomi 14 Ultra", "Xiaomi", "Xiaomi", "24030PN60G", "aurora", "aurora", "pineapple", "17", 37),
    DeviceBuildSpec("Motorola Edge 50 Ultra", "Motorola", "motorola", "XT2401-1", "aito", "aito", "sm8650", "17", 37),
    DeviceBuildSpec("Nothing Phone (3a)", "Nothing", "Nothing", "A059", "asteroids", "asteroids", "sm7635", "17", 37),
    DeviceBuildSpec("ASUS Zenfone 10", "asus", "asus", "AI2302", "AI2302", "AI2302", "sm8550", "17", 37),
    DeviceBuildSpec("OPPO Find X8 Pro", "OPPO", "OPPO", "CPH2659", "haotian", "haotian", "mt6991", "17", 37),
    DeviceBuildSpec("vivo X200 Pro", "vivo", "vivo", "V2413", "thorne", "thorne", "mt6989", "17", 37),
    DeviceBuildSpec("realme GT 6T", "realme", "realme", "RMX3853", "RMX3853", "RMX3853", "sm8635", "17", 37)
)

private val testDeviceProfiles = testDeviceBuildSpecs.mapIndexed { index, spec ->
    spec.toProfile(index + 1)
}

private val nubiaZ17StockProfile = CloneBuildProfile(
    name = "Nubia Z17",
    summary = "NX563J · Android 9 stock build sample",
    props = CloneBuildProps(
        deviceName = "Nubia Z17",
        manufacturer = "nubia",
        brand = "nubia",
        model = "NX563J",
        product = "NX563J",
        device = "NX563J",
        board = "msm8998",
        radio = "MPSS.AT.2.0.c2-M8998",
        bootloader = "NUBIA_NX563J",
        fingerprint = "nubia/NX563J/NX563J:9/PKQ1.181021.001/eng.nubia.20191015.204132:user/release-keys",
        build = "PKQ1.181021.001",
        host = "nubia-build-host",
        time = "1571172092000",
        osVersion = "4.4",
        release = "9",
        sdkInt = "28"
    )
)

internal val cloneBuildProfiles: List<CloneBuildProfile> = testDeviceProfiles + nubiaZ17StockProfile
