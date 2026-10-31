package com.almus.downloader.download

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Speed and ETA as published by the repository while a (fake) system download progresses. */
class ProgressTrackingTest {
    private lateinit var dao: FakeDao
    private lateinit var system: FakeSystem
    private lateinit var repo: DownloadRepository
    private var t = 0L
    private val mb = 1024L * 1024

    @Before fun setUp() {
        dao = FakeDao()
        system = FakeSystem()
        repo = DownloadRepository(dao, system, FakeStorage(), FakeNotifier(), FakeSettings(3)) { t }
    }

    private suspend fun startOne(length: Long = 100 * mb): Pair<Long, Long> {
        val id = (repo.start(linkInfo(length = length)) as StartResult.Started).id
        return id to dao.getById(id)!!.systemId!!
    }

    private fun report(systemId: Long, bytes: Long, total: Long = 100 * mb) {
        system.snapshots[systemId] = SystemSnapshot(StatusMapper.STATUS_RUNNING, 0, bytes, total, null, null)
    }

    @Test fun noSpeedAtFirstThenSpeedThenEta() = runBlocking<Unit> {
        val (id, sid) = startOne()
        val seen = mutableListOf<SpeedInfo?>()
        for (second in 0..7) {
            t = second * 1000L
            report(sid, second * mb)
            repo.refresh()
            seen += repo.speeds.value[id]
        }
        assertNull("second 0", seen[0])
        assertNull("second 1: too little data", seen[1])
        assertNotNull("speed known by second 3", seen[3])
        assertNull("no ETA yet at second 3", seen[3]!!.etaSeconds)
        assertEquals(mb, seen[3]!!.bytesPerSecond)
        val last = seen[7]!!
        assertEquals(mb, last.bytesPerSecond)
        assertEquals("93 MB left at 1 MB/s", 93L, last.etaSeconds)
    }

    @Test fun unknownTotalGivesSpeedButNeverAnEta() = runBlocking<Unit> {
        val (id, sid) = startOne(length = -1)
        for (second in 0..8) {
            t = second * 1000L
            report(sid, second * mb, total = -1)
            repo.refresh()
        }
        val info = repo.speeds.value[id]!!
        assertNotNull(info.bytesPerSecond)
        assertNull(info.etaSeconds)
    }

    @Test fun speedDisappearsWhenTheDownloadStopsBeingActive() = runBlocking<Unit> {
        val (id, sid) = startOne()
        for (second in 0..6) {
            t = second * 1000L
            report(sid, second * mb)
            repo.refresh()
        }
        assertNotNull(repo.speeds.value[id])

        t = 7_000
        system.snapshots[sid] = SystemSnapshot(StatusMapper.STATUS_PAUSED, StatusMapper.PAUSED_WAITING_FOR_NETWORK, 6 * mb, 100 * mb, null, null)
        repo.refresh()
        assertNull("paused downloads show no speed", repo.speeds.value[id])
    }

    @Test fun speedIsClearedAfterCompletionAndNothingIsLeftBehind() = runBlocking<Unit> {
        val (id, sid) = startOne()
        for (second in 0..6) {
            t = second * 1000L
            report(sid, second * mb)
            repo.refresh()
        }
        t = 7_000
        system.snapshots[sid] = SystemSnapshot(StatusMapper.STATUS_SUCCESSFUL, 0, 100 * mb, 100 * mb, null, null)
        repo.refresh()
        assertTrue(repo.speeds.value.isEmpty())
    }

    @Test fun cancellingForgetsTheSpeed() = runBlocking<Unit> {
        val (id, sid) = startOne()
        for (second in 0..6) {
            t = second * 1000L
            report(sid, second * mb)
            repo.refresh()
        }
        assertNotNull(repo.speeds.value[id])
        repo.cancel(id)
        assertNull(repo.speeds.value[id])
    }

    @Test fun twoDownloadsAreTrackedIndependently() = runBlocking<Unit> {
        val (idA, sidA) = startOne()
        val idB = (repo.start(linkInfo(name = "b.zip", length = 100 * mb, url = "https://example.com/b.zip")) as StartResult.Started).id
        val sidB = dao.getById(idB)!!.systemId!!
        for (second in 0..7) {
            t = second * 1000L
            report(sidA, second * mb)
            report(sidB, second * 4 * mb)
            repo.refresh()
        }
        assertEquals(mb, repo.speeds.value[idA]!!.bytesPerSecond)
        assertEquals(4 * mb, repo.speeds.value[idB]!!.bytesPerSecond)
    }
}
