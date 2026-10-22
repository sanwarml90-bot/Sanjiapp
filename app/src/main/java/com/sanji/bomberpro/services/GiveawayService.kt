package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.Giveaway
import com.sanji.bomberpro.data.GiveawayEntry
import com.sanji.bomberpro.data.GiveawayRequirements
import com.sanji.bomberpro.data.GiveawayWinner
import com.sanji.bomberpro.data.LeaderboardRow
import com.sanji.bomberpro.data.ParticipationReward
import com.sanji.bomberpro.data.PrizeItem
import com.sanji.bomberpro.data.VoteLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GiveawayService {

    private val db = FirebaseFirestore.getInstance()
    private val userData = UserDataService()

    // ============================================================
    //  CREATE
    // ============================================================
    suspend fun createGiveaway(
        createdBy: String,
        title: String,
        prize: String,
        description: String,
        channelId: String,
        channelLink: String,
        endsAt: Long,
        winnersCount: Int,
        winnerMethod: String,
        requirements: GiveawayRequirements,
        participationReward: ParticipationReward,
        prizeConfig: Map<String, List<PrizeItem>>,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis() / 1000L
            if (title.isBlank()) return@withContext Result.failure(Exception("Title required"))
            if (endsAt <= now) return@withContext Result.failure(Exception("End time must be in the future"))
            if (winnersCount < 1 || winnersCount > 100) {
                return@withContext Result.failure(Exception("Winners must be 1-100"))
            }

            val ref = db.collection(FirebaseConfig.COL_GIVEAWAYS).document()
            ref.set(
                mapOf(
                    "id" to ref.id,
                    "title" to title.trim().take(80),
                    "prize" to prize.trim().take(200),
                    "description" to description.trim().take(800),
                    "channelId" to channelId,
                    "channelLink" to channelLink,
                    "startedAt" to now,
                    "endsAt" to endsAt,
                    "status" to "active",
                    "winnersCount" to winnersCount.toLong(),
                    "winnerMethod" to winnerMethod,
                    "requirements" to mapOf(
                        "forceJoin" to requirements.forceJoin,
                        "minRefs" to requirements.minRefs.toLong(),
                        "minCredits" to requirements.minCredits.toLong(),
                        "minDays" to requirements.minDays.toLong(),
                    ),
                    "participationReward" to mapOf(
                        "enabled" to participationReward.enabled,
                        "credits" to participationReward.credits.toLong(),
                        "coins" to participationReward.coins.toLong(),
                        "distributed" to false,
                    ),
                    "prizeConfig" to prizeConfig.mapValues { (_, items) ->
                        items.map { item ->
                            mapOf(
                                "type" to item.type,
                                "amount" to item.amount.toLong(),
                                "text" to item.text,
                            )
                        }
                    },
                    "entries" to emptyMap<String, Any>(),
                    "winners" to emptyList<Any>(),
                    "createdBy" to createdBy,
                    "endedAt" to 0L,
                    "endedBy" to "",
                    "announcementRequested" to false,
                    "announcementPosted" to false,
                    "endAnnouncementRequested" to false,
                )
            ).await()

            // queue announcement for bot to post to channel
            try {
                val annRef = db.collection("giveaway_announce_requests").document()
                annRef.set(
                    mapOf(
                        "id" to annRef.id,
                        "gwId" to ref.id,
                        "type" to "announce",
                        "channelId" to channelId,
                        "title" to title.trim(),
                        "prize" to prize.trim(),
                        "description" to description.trim(),
                        "endsAt" to endsAt,
                        "winnersCount" to winnersCount.toLong(),
                        "winnerMethod" to winnerMethod,
                        "createdAt" to now,
                        "status" to "pending",
                    )
                ).await()
                ref.update("announcementRequested", true).await()
            } catch (_: Exception) {}

            // audit
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to createdBy,
                        "action" to "giveaway_created",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "gw=${ref.id} winners=$winnersCount",
                        "ts" to now,
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(ref.id)
        } catch (e: Exception) {
            Log.e(TAG, "createGiveaway failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LIST
    // ============================================================
    suspend fun listActive(limit: Long = 50): List<Giveaway> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_GIVEAWAYS)
                .whereEqualTo("status", "active")
                .orderBy("endsAt", Query.Direction.ASCENDING)
                .limit(limit)
                .get().await()
                .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            Log.w(TAG, "listActive failed", e)
            emptyList()
        }
    }

    suspend fun listEnded(limit: Long = 50): List<Giveaway> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_GIVEAWAYS)
                .whereEqualTo("status", "ended")
                .orderBy("endedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
                .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            Log.w(TAG, "listEnded failed", e)
            emptyList()
        }
    }

    // ============================================================
    //  GET
    // ============================================================
    suspend fun getGiveaway(gwId: String): Giveaway? = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_GIVEAWAYS).document(gwId).get().await()
            if (!snap.exists()) return@withContext null
            parse(snap.id, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    //  ENTER — check requirements, add entry, distribute participation
    // ============================================================
    suspend fun enterGiveaway(
        gwId: String,
        uid: String,
        userName: String,
        userRefCount: Int,
        userCredits: Int,
        userDaysOld: Int,
        isForceJoinVerified: Boolean,
    ): Result<Giveaway> = withContext(Dispatchers.IO) {
        try {
            val gw = getGiveaway(gwId)
                ?: return@withContext Result.failure(Exception("Giveaway not found"))

            if (gw.status != "active") {
                return@withContext Result.failure(Exception("Giveaway ended"))
            }
            if (System.currentTimeMillis() / 1000L > gw.endsAt) {
                return@withContext Result.failure(Exception("Giveaway has ended"))
            }
            if (gw.entries.containsKey(uid)) {
                return@withContext Result.failure(Exception("Already entered"))
            }

            val req = gw.requirements
            if (req.forceJoin && !isForceJoinVerified) {
                return@withContext Result.failure(Exception("Join required channels first"))
            }
            if (req.minRefs > 0 && userRefCount < req.minRefs) {
                return@withContext Result.failure(Exception("Need ${req.minRefs} referrals (you have $userRefCount)"))
            }
            if (req.minCredits > 0 && userCredits < req.minCredits) {
                return@withContext Result.failure(Exception("Need ${req.minCredits} credits (you have $userCredits)"))
            }
            if (req.minDays > 0 && userDaysOld < req.minDays) {
                return@withContext Result.failure(Exception("Account must be ${req.minDays} days old"))
            }

            // atomic entry write
            val now = System.currentTimeMillis() / 1000L
            val entryRef = db.collection(FirebaseConfig.COL_GIVEAWAYS).document(gwId)
            db.runTransaction { txn ->
                val snap = txn.get(entryRef)
                val existingEntries = (snap.get("entries") as? Map<*, *>) ?: emptyMap<Any, Any>()
                if (existingEntries.containsKey(uid)) {
                    throw IllegalStateException("ALREADY_ENTERED")
                }
                txn.update(
                    entryRef,
                    "entries.$uid",
                    mapOf(
                        "uid" to uid,
                        "name" to userName.take(64),
                        "enteredAt" to now,
                        "bonusVotes" to 0L,
                        "penaltyVotes" to 0L,
                    ),
                )
            }.await()

            // participation reward
            if (gw.participationReward.enabled && !gw.participationReward.distributed) {
                try {
                    val cr = gw.participationReward.credits
                    val co = gw.participationReward.coins
                    if (cr > 0) userData.addCredits(uid, cr, "giveaway-${gwId}-participate")
                    if (co > 0) userData.addCoins(uid, co, "giveaway-${gwId}-participate")
                } catch (_: Exception) {}
            }

            // notification
            try {
                val notifRef = db.collection(FirebaseConfig.COL_NOTIFICATIONS).document()
                notifRef.set(
                    mapOf(
                        "id" to notifRef.id,
                        "uid" to uid,
                        "title" to "Joined giveaway",
                        "body" to "You entered: ${gw.title}",
                        "type" to "giveaway",
                        "read" to false,
                        "ts" to now,
                    )
                )
            } catch (_: Exception) {}

            val updated = getGiveaway(gwId)
                ?: return@withContext Result.failure(Exception("Entered but failed to reload"))
            Result.success(updated)
        } catch (e: Exception) {
            if (e.message?.contains("ALREADY_ENTERED", true) == true) {
                Result.failure(Exception("Already entered"))
            } else {
                Log.e(TAG, "enterGiveaway failed", e)
                Result.failure(e)
            }
        }
    }

    // ============================================================
    //  ADJUST VOTES (admin, anonymous)
    // ============================================================
    suspend fun adjustVotes(
        gwId: String,
        adminUid: String,
        targetUid: String,
        delta: Int,
        reason: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (delta == 0) return@withContext Result.failure(Exception("Delta must not be 0"))
            val gwRef = db.collection(FirebaseConfig.COL_GIVEAWAYS).document(gwId)
            val gw = getGiveaway(gwId)
                ?: return@withContext Result.failure(Exception("Giveaway not found"))
            if (!gw.entries.containsKey(targetUid)) {
                return@withContext Result.failure(Exception("User not entered"))
            }

            val field = if (delta > 0) "bonusVotes" else "penaltyVotes"
            val absDelta = kotlin.math.abs(delta).toLong()

            db.runTransaction { txn ->
                val snap = txn.get(gwRef)
                val entries = snap.get("entries") as? Map<*, *> ?: emptyMap<Any, Any>()
                val entry = entries[targetUid] as? Map<*, *> ?: throw IllegalStateException("USER_NOT_ENTERED")
                val current = ((entry[field] as? Number)?.toLong() ?: 0L)
                txn.update(gwRef, "entries.$targetUid.$field", current + absDelta)
            }.await()

            // log
            val logRef = db.collection("giveaway_vote_logs").document()
            logRef.set(
                mapOf(
                    "id" to logRef.id,
                    "gwId" to gwId,
                    "adminUid" to adminUid,
                    "targetUid" to targetUid,
                    "delta" to delta.toLong(),
                    "reason" to reason.take(100),
                    "ts" to System.currentTimeMillis() / 1000L,
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "adjustVotes failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LEADERBOARD
    // ============================================================
    suspend fun computeLeaderboard(gw: Giveaway): List<LeaderboardRow> = withContext(Dispatchers.IO) {
        if (gw.entries.isEmpty()) return@withContext emptyList()

        // fetch refs for each entry (small numbers)
        val rows = mutableListOf<LeaderboardRow>()
        for ((uid, entry) in gw.entries) {
            val refCount = try { userData.getReferralCount(uid) } catch (_: Exception) { 0 }
            val votes = entry.netVotes
            val score = when (gw.winnerMethod) {
                "votes_only" -> votes.toDouble()
                "refs_only" -> refCount.toDouble()
                else -> votes.toDouble() + refCount * 0.5
            }
            rows.add(
                LeaderboardRow(
                    uid = uid,
                    name = entry.name.ifBlank { "User" },
                    votes = votes,
                    refs = refCount,
                    score = score,
                )
            )
        }
        val sorted = rows.sortedWith(
            compareByDescending<LeaderboardRow> { it.score }
                .thenByDescending { it.votes }
                .thenByDescending { it.refs }
                .thenBy { it.uid }
        )
        sorted.mapIndexed { i, r -> r.copy(rank = i + 1) }
    }

    // ============================================================
    //  END — compute winners, distribute prizes, mark ended
    // ============================================================
    suspend fun endGiveaway(
        gwId: String,
        adminUid: String,
        requestChannelPost: Boolean = true,
    ): Result<Giveaway> = withContext(Dispatchers.IO) {
        try {
            val gw = getGiveaway(gwId)
                ?: return@withContext Result.failure(Exception("Giveaway not found"))
            if (gw.status != "active") {
                return@withContext Result.failure(Exception("Already ended"))
            }

            val now = System.currentTimeMillis() / 1000L
            val board = computeLeaderboard(gw)

            val winners = board.take(gw.winnersCount).mapIndexed { i, row ->
                val rank = i + 1
                val prizes = gw.prizeConfig[rank.toString()] ?: emptyList()
                val awarded = mutableListOf<String>()
                for (p in prizes) {
                    try {
                        when (p.type) {
                            "credits" -> {
                                if (p.amount > 0) {
                                    userData.addCredits(row.uid, p.amount, "giveaway-${gwId}-rank-$rank")
                                    awarded.add("💰 +${p.amount} credits")
                                }
                            }
                            "coins" -> {
                                if (p.amount > 0) {
                                    userData.addCoins(row.uid, p.amount, "giveaway-${gwId}-rank-$rank")
                                    awarded.add("🪙 +${p.amount} coins")
                                }
                            }
                            "custom" -> {
                                if (p.text.isNotBlank()) awarded.add("📦 ${p.text}")
                            }
                        }
                    } catch (_: Exception) {}
                }

                // DM winner via notification
                try {
                    val notifRef = db.collection(FirebaseConfig.COL_NOTIFICATIONS).document()
                    notifRef.set(
                        mapOf(
                            "id" to notifRef.id,
                            "uid" to row.uid,
                            "title" to "🏆 You won!",
                            "body" to "Rank #$rank in ${gw.title}. Prizes: ${awarded.joinToString(", ")}",
                            "type" to "giveaway",
                            "read" to false,
                            "ts" to now,
                        )
                    )
                } catch (_: Exception) {}

                GiveawayWinner(
                    uid = row.uid,
                    name = row.name,
                    rank = rank,
                    votes = row.votes,
                    refs = row.refs,
                    score = row.score,
                    prizesAwarded = awarded,
                )
            }

            // distribute participation if not yet
            if (gw.participationReward.enabled && !gw.participationReward.distributed) {
                for ((uid, _) in gw.entries) {
                    try {
                        if (gw.participationReward.credits > 0) {
                            userData.addCredits(uid, gw.participationReward.credits, "giveaway-${gwId}-participate")
                        }
                        if (gw.participationReward.coins > 0) {
                            userData.addCoins(uid, gw.participationReward.coins, "giveaway-${gwId}-participate")
                        }
                    } catch (_: Exception) {}
                }
            }

            val winnersMap = winners.map { w ->
                mapOf(
                    "uid" to w.uid,
                    "name" to w.name,
                    "rank" to w.rank.toLong(),
                    "votes" to w.votes.toLong(),
                    "refs" to w.refs.toLong(),
                    "score" to w.score,
                    "prizesAwarded" to w.prizesAwarded,
                )
            }

            val gwRef = db.collection(FirebaseConfig.COL_GIVEAWAYS).document(gwId)
            gwRef.update(
                "status", "ended",
                "endedAt", now,
                "endedBy", adminUid,
                "winners", winnersMap,
                "participationReward.distributed", true,
            ).await()

            // queue channel end-post request for bot
            if (requestChannelPost) {
                try {
                    val annRef = db.collection("giveaway_announce_requests").document()
                    annRef.set(
                        mapOf(
                            "id" to annRef.id,
                            "gwId" to gwId,
                            "type" to "end",
                            "channelId" to gw.channelId,
                            "title" to gw.title,
                            "prize" to gw.prize,
                            "winners" to winners.map { w ->
                                mapOf(
                                    "name" to w.name,
                                    "uid" to w.uid,
                                    "rank" to w.rank.toLong(),
                                    "votes" to w.votes.toLong(),
                                    "refs" to w.refs.toLong(),
                                    "prizes" to w.prizesAwarded,
                                )
                            },
                            "participationUsers" to gw.entries.size.toLong(),
                            "createdAt" to now,
                            "status" to "pending",
                        )
                    ).await()
                    gwRef.update("endAnnouncementRequested", true).await()
                } catch (_: Exception) {}
            }

            // audit
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to adminUid,
                        "action" to "giveaway_ended",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "gw=$gwId winners=${winners.size}",
                        "ts" to now,
                    )
                ).await()
            } catch (_: Exception) {}

            getGiveaway(gwId) ?: Result.failure<Giveaway>(Exception("Ended but reload failed")).getOrNull()
                ?: return@withContext Result.failure(Exception("Reload failed"))
        } catch (e: Exception) {
            Log.e(TAG, "endGiveaway failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  CANCEL
    // ============================================================
    suspend fun cancelGiveaway(gwId: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_GIVEAWAYS).document(gwId).update(
                    "status", "cancelled",
                    "endedAt", System.currentTimeMillis() / 1000L,
                    "endedBy", adminUid,
                ).await()
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  VOTE LOG
    // ============================================================
    suspend fun getVoteLog(gwId: String, limit: Long = 100): List<VoteLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                db.collection("giveaway_vote_logs")
                    .whereEqualTo("gwId", gwId)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.map { d ->
                        VoteLogEntry(
                            id = d.id,
                            gwId = d.getString("gwId") ?: "",
                            adminUid = d.getString("adminUid") ?: "",
                            targetUid = d.getString("targetUid") ?: "",
                            delta = (d.getLong("delta") ?: 0L).toInt(),
                            reason = d.getString("reason") ?: "",
                            ts = d.getLong("ts") ?: 0L,
                        )
                    }
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ============================================================
    //  PARSE
    // ============================================================
    private fun parse(id: String, map: Map<String, Any?>): Giveaway? {
        if (map.isEmpty()) return null

        val req = map["requirements"] as? Map<*, *>
        val reward = map["participationReward"] as? Map<*, *>
        val rawEntries = map["entries"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val rawWinners = map["winners"] as? List<*> ?: emptyList<Any>()
        val rawPrizeConfig = map["prizeConfig"] as? Map<*, *> ?: emptyMap<Any, Any>()

        val entries = rawEntries.entries.mapNotNull { (k, v) ->
            val uid = k.toString()
            val em = v as? Map<*, *> ?: return@mapNotNull null
            uid to GiveawayEntry(
                uid = uid,
                name = em["name"] as? String ?: "User",
                enteredAt = (em["enteredAt"] as? Number)?.toLong() ?: 0L,
                bonusVotes = (em["bonusVotes"] as? Number)?.toInt() ?: 0,
                penaltyVotes = (em["penaltyVotes"] as? Number)?.toInt() ?: 0,
            )
        }.toMap()

        val winners = rawWinners.mapNotNull { w ->
            val m = w as? Map<*, *> ?: return@mapNotNull null
            GiveawayWinner(
                uid = m["uid"] as? String ?: "",
                name = m["name"] as? String ?: "User",
                rank = (m["rank"] as? Number)?.toInt() ?: 0,
                votes = (m["votes"] as? Number)?.toInt() ?: 0,
                refs = (m["refs"] as? Number)?.toInt() ?: 0,
                score = (m["score"] as? Number)?.toDouble() ?: 0.0,
                prizesAwarded = (m["prizesAwarded"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            )
        }

        val prizeConfig = rawPrizeConfig.entries.mapNotNull { (k, v) ->
            val rank = k.toString()
            val items = v as? List<*> ?: return@mapNotNull null
            rank to items.mapNotNull { itm ->
                val m = itm as? Map<*, *> ?: return@mapNotNull null
                PrizeItem(
                    type = m["type"] as? String ?: "credits",
                    amount = (m["amount"] as? Number)?.toInt() ?: 0,
                    text = m["text"] as? String ?: "",
                )
            }
        }.toMap()

        return Giveaway(
            id = id,
            title = map["title"] as? String ?: "",
            prize = map["prize"] as? String ?: "",
            description = map["description"] as? String ?: "",
            channelId = map["channelId"] as? String ?: "",
            channelLink = map["channelLink"] as? String ?: "",
            startedAt = (map["startedAt"] as? Number)?.toLong() ?: 0L,
            endsAt = (map["endsAt"] as? Number)?.toLong() ?: 0L,
            status = map["status"] as? String ?: "active",
            winnersCount = (map["winnersCount"] as? Number)?.toInt() ?: 1,
            winnerMethod = map["winnerMethod"] as? String ?: "votes_and_refs",
            requirements = GiveawayRequirements(
                forceJoin = req?.get("forceJoin") as? Boolean ?: true,
                minRefs = (req?.get("minRefs") as? Number)?.toInt() ?: 0,
                minCredits = (req?.get("minCredits") as? Number)?.toInt() ?: 0,
                minDays = (req?.get("minDays") as? Number)?.toInt() ?: 0,
            ),
            participationReward = ParticipationReward(
                enabled = reward?.get("enabled") as? Boolean ?: false,
                credits = (reward?.get("credits") as? Number)?.toInt() ?: 0,
                coins = (reward?.get("coins") as? Number)?.toInt() ?: 0,
                distributed = reward?.get("distributed") as? Boolean ?: false,
            ),
            prizeConfig = prizeConfig,
            entries = entries,
            winners = winners,
            createdBy = map["createdBy"] as? String ?: "",
            endedAt = (map["endedAt"] as? Number)?.toLong() ?: 0L,
            endedBy = map["endedBy"] as? String ?: "",
            announcementRequested = map["announcementRequested"] as? Boolean ?: false,
            announcementPosted = map["announcementPosted"] as? Boolean ?: false,
            endAnnouncementRequested = map["endAnnouncementRequested"] as? Boolean ?: false,
        )
    }

    companion object {
        private const val TAG = "GiveawayService"
    }
}