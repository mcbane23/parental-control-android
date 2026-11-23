package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    onAddChild: () -> Unit,
    onInviteParent: () -> Unit,
    onDismissCode: () -> Unit,
    onSetFullControl: (Member, Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val me = members.firstOrNull { it.uid == myUid }
    val iAmOwner = family.ownerUid == myUid
    val canManage = iAmOwner || me?.fullControl == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                family.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item { Text("Children", style = MaterialTheme.typography.titleMedium) }
        val children = members.filter { it.role == Role.CHILD }
        if (children.isEmpty()) {
            item { Text("No child phones are paired yet.") }
        }
        items(children, key = { it.uid }) { child ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(child.name, style = MaterialTheme.typography.titleMedium)
                    Text("Paired. Limits, apps and location arrive in the next steps.")
                }
            }
        }
        item { Text("Parents", style = MaterialTheme.typography.titleMedium) }
        items(members.filter { it.role == Role.PARENT }, key = { it.uid }) { parent ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
        if (canManage) {
            item {
                Button(onClick = onAddChild, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Add child's phone")
                }
            }
            item {
                OutlinedButton(onClick = onInviteParent, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Invite another parent")
                }
            }
        }
        item { UpdateCard() }
        item {
            TextButton(onClick = onSignOut, enabled = !busy) { Text("Sign out") }
        }
    }

    if (pairingCode != null) {
        AlertDialog(
            onDismissRequest = onDismissCode,
            confirmButton = { TextButton(onClick = onDismissCode) { Text("Done") } },
            title = {
                Text(if (pairingCode.role == Role.CHILD) "Pair child's phone" else "Invite a parent")
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (pairingCode.role == Role.CHILD) {
                            "Open the app on your child's phone, choose \"This is my child's phone\" and type this code."
                        } else {
                            "The other parent signs in with Google, then types this code under \"Join one with an invite code\"."
                        },
                    )
                    Text(
                        pairingCode.code,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                    Text("The code works once and expires in ${PairingCodes.VALID_MINUTES} minutes.")
                }
            },
        )
    }
}

@Composable
fun ChildHomeScreen(family: Family) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This phone is paired", style = MaterialTheme.typography.headlineSmall)
        Text("Family: ${family.name}", style = MaterialTheme.typography.titleMedium)
        Text(
            "Time limits, app rules and location sharing will appear here.",
            textAlign = TextAlign.Center,
        )
        UpdateCard()
    }
}
