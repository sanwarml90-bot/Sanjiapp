package com.sanji.bomberpro.data

data class Giveaway(
    val id: String = "",
    val title: String = "",
    val prize: String = "",
    val description: String = "",
    val channelId: String = "",
    val channelLink: String = "",
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val status: String = "active",
    val winnersCount: Int = 1,
    val winnerMethod: String = "votes_and_refs",
    val requirements: GiveawayRequirements = GiveawayRequirements(),
    val participationReward: ParticipationReward = ParticipationReward(),
    val prizeConfig: Map<String, List<PrizeItem>> = emptyMap(),
    val entries: Map<String, GiveawayEntry> = emptyMap(),
    val winners: List<GiveawayWinner> = emptyList(),
    val createdBy: String = "",
    val endedAt: Long = 0L,
    val endedBy: String = "",
    val announcementRequested: Boolean = false,
    val announcementPosted: Boolean = false,
    val endAnnouncementRequested: Boolean = false,
)

data class GiveawayRequirements(
    val forceJoin: Boolean = true,
    val minRefs: Int = 0,
    val minCredits: Int = 0,
    val minDays: Int = 0,
)

data class ParticipationReward(
    val enabled: Boolean = false,
    val credits: Int = 0,
    val coins: Int = 0,
    val distributed: Boolean = false,
)

data class GiveawayEntry(
    val uid: String = "",
    val name: String = "",
    val enteredAt: Long = 0L,
    val bonusVotes: Int = 0,
    val penaltyVotes: Int = 0,
) {
    val netVotes: Int get() = bonusVotes - penaltyVotes
}

data class GiveawayWinner(
    val uid: String = "",
    val name: String = "",
    val rank: Int = 0,
    val votes: Int = 0,
    val refs: Int = 0,
    val score: Double = 0.0,
    val prizesAwarded: List<String> = emptyList(),
)

data class PrizeItem(
    val type: String = "credits",
    val amount: Int = 0,
    val text: String = "",
)

data class LeaderboardRow(
    val rank: Int = 0,
    val uid: String = "",
    val name: String = "",
    val votes: Int = 0,
    val refs: Int = 0,
    val score: Double = 0.0,
)

data class VoteLogEntry(
    val id: String = "",
    val gwId: String = "",
    val adminUid: String = "",
    val targetUid: String = "",
    val delta: Int = 0,
    val reason: String = "",
    val ts: Long = 0L,
)