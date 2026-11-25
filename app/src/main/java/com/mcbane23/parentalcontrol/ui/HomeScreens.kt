package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcbane23.parentalcontrol.data.Family
import com.mcbane23.parentalcontrol.data.Member
import com.mcbane23.parentalcontrol.data.PairingCode
import com.mcbane23.parentalcontrol.data.PairingCodes
import com.mcbane23.parentalcontrol.data.Role

@Composable
fun ParentHomeScreen(
    family: Family,
    myUid: String,
    members: List<Member>,
    busy: Boolean,
    pairingCode: PairingCode?,
    /** Showing the made-up demo family. */
    demo: Boolean,
    onDemo: () -> Unit,
    onAddChild: () -> Unit,
    onInviteParent: () -> Unit,
    onDismissCode: () -> Unit,
    onSetFullControl: (Member, Boolean) -> Unit,
    onOpenChild: (Member) -> Unit,
    onSignOut: () -> Unit,
) {
    val me = members.firstOrNull { it.uid == myUid }
    val iAmOwner = family.ownerUid == myUid
    val canManage = iAmOwner || me?.fullControl == true
    val children = members.filter { it.role == Role.CHILD }
    val parents = members.filter { it.role == Role.PARENT }

    AdaptivePanes(
        header = {
            AppHeader(
                title = family.name,
                subtitle = "${count(children.size, "child", "children")}, ${count(parents.size, "parent", "parents")}",
            )
        },
        first = {
            item { SectionHeader("Children") }
            if (children.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Face,
                        title = "No child phones are paired yet",
                        body = if (canManage) {
                            "Tap \"Add child's phone\" to get a pairing code for it."
                        } else {
                            "A parent with full control can add one."
                        },
                    )
                }
                if (!demo) {
                    item {
                        TextButton(onClick = onDemo, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("See it with a demo child")
                        }
                    }
                }
            }
            items(children, key = { it.uid }) { child ->
                AppCard(onClick = { onOpenChild(child) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(child.name)
                        Column(Modifier.weight(1f)) {
                            Text(child.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap to see usage and set limits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (canManage) {
                item {
                    Button(
                        onClick = onAddChild,
                        enabled = !busy,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add child's phone")
                    }
                }
            }
        },
        second = {
            item { SectionHeader("Parents") }
            item {
                AppCard {
                    parents.forEachIndexed { index, parent ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(parent.name, size = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (parent.uid == myUid) "${parent.name} (you)" else parent.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    when {
                                        parent.owner -> "Main parent"
                                        parent.fullControl -> "Full control"
                                        else -> "Can view only"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (iAmOwner && !parent.owner) {
                                Switch(
                                    checked = parent.fullControl,
                                    onCheckedChange = { onSetFullControl(parent, it) },
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                }
            }
            if (canManage) {
                item {
                    OutlinedButton(
                        onClick = onInviteParent,
                        enabled = !busy,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Invite another parent")
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppVersion(Modifier.weight(1f).padding(start = 4.dp))
                    TextButton(onClick = onSignOut, enabled = !busy) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (demo) "Exit demo" else "Sign out")
                    }
                }
            }
        },
    )

    if (pairingCode != null) {
        AlertDialog(
            onDismissRequest = onDismissCode,
            confirmButton = { TextButton(onClick = onDismissCode) { Text("Done") } },
            title = {
                Text(if (pairingCode.role == Role.CHILD) "Pair child's phone" else "Invite a parent")
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (pairingCode.role == Role.CHILD) {
                            "Open the app on your child's phone, choose \"This is my child's phone\" and type this code."
                        } else {
                            "The other parent signs in with Google, then types this code under \"Join one with an invite code\"."
                        },
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            pairingCode.code,
                            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 4.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    }
                    Text(
                        if (demo) {
                            "This is a sample code from the demo. It does not pair a phone."
                        } else {
                            "The code works once and expires in ${PairingCodes.VALID_MINUTES} minutes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/** "1 child", "2 children". */
private fun count(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"
