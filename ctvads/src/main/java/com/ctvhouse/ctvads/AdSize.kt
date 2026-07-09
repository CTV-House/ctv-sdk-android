package com.ctvhouse.ctvads

/**
 * Banner ad size in density-independent pixels (also used as OpenRTB w/h).
 */
data class AdSize(val width: Int, val height: Int) {
    companion object {
        /** IAB standard mobile banner. */
        val BANNER = AdSize(320, 50)

        /** IAB Medium Rectangle. */
        val MREC = AdSize(300, 250)

        /** IAB Leaderboard. */
        val LEADERBOARD = AdSize(728, 90)

        /** IAB Billboard, common on large TV screens. */
        val BILLBOARD = AdSize(970, 250)

        /** Full-width HD banner strip. */
        val TV_BANNER = AdSize(1920, 200)
    }
}
