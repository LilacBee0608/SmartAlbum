package com.example.smartalbum.data

enum class ColorCategory(val labelZh: String) {
    RED("红色"),
    ORANGE("橙色"),
    YELLOW("黄色"),
    LIME("黄绿"),
    GREEN("绿色"),
    TEAL("青绿"),
    CYAN("青色"),
    SKY("天蓝"),
    BLUE("蓝色"),
    INDIGO("靛蓝"),
    PURPLE("紫色"),
    MAGENTA("品红"),
    PINK("粉色"),
    BROWN("棕色"),
    OLIVE("橄榄"),
    NAVY("深蓝"),
    MAROON("栗色"),
    GRAY("灰色"),
    BLACK("黑色"),
    WHITE("白色"),
    UNKNOWN("未分类");

    companion object {
        fun fromHsv(h: Float, s: Float, v: Float): ColorCategory {
            val brightness = v
            val saturation = s

            if (brightness < 0.12f) return BLACK
            if (brightness > 0.95f && saturation < 0.08f) return WHITE
            if (saturation < 0.08f) {
                return when {
                    brightness > 0.85f -> WHITE
                    brightness > 0.65f -> GRAY
                    brightness > 0.40f -> ColorCategory.GRAY
                    else -> ColorCategory.GRAY
                }
            }

            val hue = (h + 360f) % 360f

            if (saturation < 0.15f) {
                return when {
                    brightness > 0.75f -> WHITE
                    brightness > 0.50f -> GRAY
                    else -> ColorCategory.GRAY
                }
            }

            return categorizeByHue(hue, saturation, brightness)
        }

        private fun categorizeByHue(hue: Float, saturation: Float, brightness: Float): ColorCategory {
            val isDark = brightness < 0.45
            val isLight = brightness > 0.75
            val isVivid = saturation > 0.6
            val isMuted = saturation < 0.3

            return when {
                hue < 10f || hue >= 355f -> {
                    when {
                        isDark && !isVivid -> MAROON
                        isLight && !isVivid -> PINK
                        else -> RED
                    }
                }
                hue < 25f -> {
                    when {
                        isDark -> MAROON
                        isLight && !isVivid -> ORANGE
                        else -> ORANGE
                    }
                }
                hue < 40f -> {
                    when {
                        isDark -> BROWN
                        isLight -> YELLOW
                        else -> YELLOW
                    }
                }
                hue < 70f -> {
                    when {
                        isDark && !isVivid -> OLIVE
                        isLight -> LIME
                        else -> LIME
                    }
                }
                hue < 95f -> {
                    when {
                        isDark -> GREEN
                        isLight -> GREEN
                        else -> GREEN
                    }
                }
                hue < 140f -> {
                    when {
                        isDark -> TEAL
                        isLight -> TEAL
                        else -> TEAL
                    }
                }
                hue < 170f -> {
                    when {
                        isDark -> CYAN
                        isLight -> CYAN
                        else -> CYAN
                    }
                }
                hue < 190f -> {
                    when {
                        isDark && !isVivid -> TEAL
                        isLight -> SKY
                        else -> SKY
                    }
                }
                hue < 220f -> {
                    when {
                        isDark -> NAVY
                        isLight -> BLUE
                        else -> BLUE
                    }
                }
                hue < 250f -> {
                    when {
                        isDark -> INDIGO
                        isLight -> INDIGO
                        else -> INDIGO
                    }
                }
                hue < 275f -> {
                    when {
                        isDark && !isVivid -> PURPLE
                        isLight -> PURPLE
                        else -> PURPLE
                    }
                }
                hue < 295f -> {
                    when {
                        isDark && !isVivid -> MAGENTA
                        isLight -> MAGENTA
                        else -> MAGENTA
                    }
                }
                hue < 330f -> {
                    when {
                        isDark && !isVivid -> PINK
                        isLight && !isVivid -> PINK
                        else -> PINK
                    }
                }
                hue < 355f -> {
                    when {
                        isDark && !isVivid -> MAROON
                        isLight && !isVivid -> PINK
                        else -> RED
                    }
                }
                else -> RED
            }
        }
    }
}