"""
Sanji Payment Bot — Approve/Reject payments for Sanji Bomber Pro.

RUN:
    1. pip install -r requirements.txt
    2. Download service-account.json from Firebase Console:
       Project Settings → Service Accounts → Generate new private key
    3. Put file next to this script, or set env FIREBASE_SA
    4. Set env PAYMENT_BOT_TOKEN (defaults to the one in code)
    5. python payment_bot.py

KEEP THIS RUNNING 24/7:
    - Termux on spare phone
    - Cheap VPS (Hetzner €4/mo, etc.)
    - Railway / Render free tier
"""

import asyncio
import logging
import os
from datetime import datetime

import firebase_admin
from firebase_admin import credentials, firestore
from aiogram import Bot, Dispatcher, Router, F
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.types import CallbackQuery

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("payment_bot")

BOT_TOKEN = os.environ.get(
    "PAYMENT_BOT_TOKEN",
    "8760383147:AAFTUuYN8xKLS_70lRFdoFpwr0a5Vo8svVE",
)
OWNER_ID = int(os.environ.get("OWNER_ID", "7966027018"))
FIREBASE_SA = os.environ.get("FIREBASE_SA", "service-account.json")

if not os.path.exists(FIREBASE_SA):
    raise SystemExit(
        f"Missing Firebase service account file: {FIREBASE_SA}\n"
        "Download it from Firebase Console → Project Settings → Service Accounts"
    )

if not firebase_admin._apps:
    cred = credentials.Certificate(FIREBASE_SA)
    firebase_admin.initialize_app(cred)

db = firestore.client()
router = Router()


@router.callback_query(F.data.startswith("approve:") | F.data.startswith("reject:"))
async def handle_decision(cq: CallbackQuery):
    if cq.from_user.id != OWNER_ID:
        await cq.answer("Unauthorized", show_alert=True)
        return

    parts = cq.data.split(":", 1)
    if len(parts) != 2:
        await cq.answer("Bad data", show_alert=True)
        return

    action, request_id = parts
    log.info(f"Action {action} on request {request_id} by owner")

    try:
        req_ref = db.collection("payment_requests").document(request_id)
        req_snap = req_ref.get()

        if not req_snap.exists:
            await cq.answer("Request not found", show_alert=True)
            return

        data = req_snap.to_dict()
        if data.get("status") != "pending":
            await cq.answer(f"Already {data.get('status')}", show_alert=True)
            return

        uid = data.get("uid")
        if not uid:
            await cq.answer("No uid in request", show_alert=True)
            return

        now = int(datetime.now().timestamp())
        user_ref = db.collection("users").document(uid)

        if action == "approve":
            txn_type = data.get("type", "credits")

            if txn_type == "credits":
                amount = int(data.get("creditsAmount", 0))
                if amount > 0:
                    user_ref.update({"credits": firestore.Increment(amount)})
                    db.collection("transactions").add({
                        "uid": uid,
                        "type": "credit_add_manual",
                        "delta": amount,
                        "reason": f"payment-approved-{request_id[:8]}",
                        "ts": now,
                    })
                    log.info(f"Granted {amount} credits to {uid}")
            else:
                plan = data.get("plan", "pro")
                expires = now + 30 * 86400
                user_ref.update({
                    "subscription.plan": plan,
                    "subscription.expiresAt": expires,
                    "subscription.startedAt": now,
                })
                log.info(f"Granted {plan} subscription to {uid}")

            req_ref.update({
                "status": "approved",
                "reviewedAt": now,
                "reviewedBy": str(cq.from_user.id),
            })

            try:
                new_caption = (cq.message.caption or "") + (
                    f"\n\n<b>✅ APPROVED</b>\n"
                    f"🕐 {datetime.now().strftime('%d/%m/%Y %H:%M')}"
                )
                await cq.message.edit_caption(
                    caption=new_caption,
                    parse_mode="HTML",
                )
            except Exception as e:
                log.warning(f"edit caption failed: {e}")

            await cq.answer("✅ Approved — user credited")

        else:
            req_ref.update({
                "status": "rejected",
                "reviewedAt": now,
                "reviewedBy": str(cq.from_user.id),
            })

            try:
                new_caption = (cq.message.caption or "") + (
                    f"\n\n<b>❌ REJECTED</b>\n"
                    f"🕐 {datetime.now().strftime('%d/%m/%Y %H:%M')}"
                )
                await cq.message.edit_caption(
                    caption=new_caption,
                    parse_mode="HTML",
                )
            except Exception as e:
                log.warning(f"edit caption failed: {e}")

            await cq.answer("❌ Rejected")

    except Exception as e:
        log.exception(f"handle_decision failed: {e}")
        await cq.answer(f"Error: {str(e)[:80]}", show_alert=True)


@router.message(F.text == "/start")
async def cmd_start(msg):
    if msg.from_user.id != OWNER_ID:
        await msg.answer("This bot is private.")
        return
    await msg.answer(
        "💳 <b>Sanji Payment Bot</b>\n\n"
        "Naye payment requests yahan aayenge.\n"
        "Har request ke saath <b>Approve</b> / <b>Reject</b> buttons honge.",
        parse_mode="HTML",
    )


async def main():
    bot = Bot(token=BOT_TOKEN, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher()
    dp.include_router(router)
    me = await bot.get_me()
    log.info(f"Payment bot @{me.username} starting...")
    await dp.start_polling(bot, allowed_updates=dp.resolve_used_update_types())


if __name__ == "__main__":
    asyncio.run(main())