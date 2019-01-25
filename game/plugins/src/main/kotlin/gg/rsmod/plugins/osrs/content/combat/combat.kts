
import gg.rsmod.game.action.NpcPathAction
import gg.rsmod.game.message.impl.SetMinimapMarkerMessage
import gg.rsmod.game.model.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.FROZEN_TIMER
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.plugins.osrs.api.helper.getVarbit
import gg.rsmod.plugins.osrs.api.helper.pawn
import gg.rsmod.plugins.osrs.api.helper.setVarp
import gg.rsmod.plugins.osrs.content.combat.Combat
import gg.rsmod.plugins.osrs.content.combat.CombatConfigs
import gg.rsmod.plugins.osrs.content.combat.strategy.magic.CombatSpell

onCombat {
    it.suspendable {
        while (true) {
            if (!cycle(it)) {
                break
            }
            it.wait(1)
        }
    }
}

suspend fun cycle(it: Plugin): Boolean {
    val pawn = it.pawn()
    val target = pawn.attr[COMBAT_TARGET_FOCUS_ATTR]

    if (target == null) {
        pawn.facePawn(null)
        return false
    }

    if (!pawn.lock.canAttack()) {
        return false
    }

    pawn.facePawn(target)

    if (target.isDead()) {
        return false
    }

    if (pawn is Player) {
        pawn.setVarp(Combat.PRIORITY_PID_VARP, target.index)

        if (!pawn.attr.has(Combat.CASTING_SPELL) && pawn.getVarbit(Combat.SELECTED_AUTOCAST_VARBIT) != 0) {
            val spell = CombatSpell.values.firstOrNull { it.autoCastId == pawn.getVarbit(Combat.SELECTED_AUTOCAST_VARBIT) }
            if (spell != null) {
                pawn.attr[Combat.CASTING_SPELL] = spell
            }
        }
    }

    val strategy = CombatConfigs.getCombatStrategy(pawn)
    val attackRange = strategy.getAttackRange(pawn)

    val pathFound = NpcPathAction.walkTo(it, pawn, target, attackRange)
    if (!pathFound) {
        pawn.movementQueue.clear()
        if (pawn.getType().isNpc()) {
            /**
             * Npcs will keep trying to find a path to engage in combat.
             */
            return true
        }
        if (pawn is Player) {
            if (!pawn.timers.has(FROZEN_TIMER)) {
                pawn.message(Entity.YOU_CANT_REACH_THAT)
            }
            pawn.write(SetMinimapMarkerMessage(255, 255))
        }
        pawn.facePawn(null)
        Combat.reset(pawn)
        return false
    }

    pawn.movementQueue.clear()

    if (Combat.isAttackDelayReady(pawn)) {
        if (Combat.canAttack(pawn, target, strategy)) {
            strategy.attack(pawn, target)
            Combat.postAttack(pawn, target)
        } else {
            Combat.reset(pawn)
        }
    }
    return true
}