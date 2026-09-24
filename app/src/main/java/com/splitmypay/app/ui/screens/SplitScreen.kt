package com.splitmypay.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitmypay.app.data.local.entity.MemberEntity
import com.splitmypay.app.data.util.SplitCalculator
import com.splitmypay.app.ui.MainViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

enum class SplitMode {
    EQUAL,
    CUSTOM,
    WEIGHTS
}

val CATEGORIES = listOf(
    "FOOD_AND_DRINK" to "Food & Drink",
    "GROCERIES" to "Groceries",
    "SHOPPING" to "Shopping",
    "TRANSPORT" to "Transport",
    "ENTERTAINMENT" to "Entertainment",
    "TRAVEL" to "Travel",
    "OTHER" to "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    captureId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val capture by viewModel.getCapture(captureId).collectAsState(initial = null)
    val tricounts by viewModel.tricounts.collectAsState()
    val defaultTricount by viewModel.defaultTricount.collectAsState()

    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("EUR") }

    var selectedTricountId by remember { mutableStateOf<Long?>(null) }
    var members by remember { mutableStateOf<List<MemberEntity>>(emptyList()) }
    var selectedPayerUuid by remember { mutableStateOf<String?>(null) }

    var splitMode by remember { mutableStateOf(SplitMode.EQUAL) }
    var selectedCategory by remember { mutableStateOf("FOOD_AND_DRINK") }

    val equalSelectedMembers = remember { mutableStateMapOf<String, Boolean>() }
    val customAmounts = remember { mutableStateMapOf<String, String>() }
    val memberWeights = remember { mutableStateMapOf<String, Int>() }

    var isSubmitting by remember { mutableStateOf(false) }
    var submissionSuccess by remember { mutableStateOf(false) }

    // Initialize from capture
    LaunchedEffect(capture) {
        capture?.let {
            if (description.isEmpty()) description = it.merchant
            if (amountText.isEmpty()) amountText = String.format(Locale.US, "%.2f", it.amount)
            currency = it.currency
        }
    }

    // Default target group
    LaunchedEffect(tricounts, defaultTricount) {
        if (selectedTricountId == null) {
            selectedTricountId = defaultTricount?.id ?: tricounts.firstOrNull()?.id
        }
    }

    // Load members when selected group changes
    LaunchedEffect(selectedTricountId) {
        selectedTricountId?.let { id ->
            viewModel.getMembersForTricount(id).collect { mList ->
                members = mList
                val currentPayer = mList.find { it.isCurrentUser } ?: mList.firstOrNull()
                if (selectedPayerUuid == null) {
                    selectedPayerUuid = currentPayer?.uuid
                }
                mList.forEach { m ->
                    if (!equalSelectedMembers.containsKey(m.uuid)) equalSelectedMembers[m.uuid] = true
                    if (!memberWeights.containsKey(m.uuid)) memberWeights[m.uuid] = 1
                    if (!customAmounts.containsKey(m.uuid)) customAmounts[m.uuid] = ""
                }
            }
        }
    }

    val totalAmount = amountText.toDoubleOrNull() ?: 0.0
    val activeUuids = remember(members, equalSelectedMembers.toMap()) {
        members.filter { equalSelectedMembers[it.uuid] == true }.map { it.uuid }
    }
    val equalAllocations = remember(totalAmount, activeUuids) {
        SplitCalculator.calculateEqualSplit(totalAmount, activeUuids)
    }.associateBy { it.memberUuid }

    val currentWeights = remember(members, memberWeights.toMap()) {
        members.associate { it.uuid to (memberWeights[it.uuid] ?: 1) }
    }
    val weightedAllocs = remember(totalAmount, currentWeights) {
        SplitCalculator.calculateWeightedSplit(totalAmount, currentWeights)
    }.associateBy { it.memberUuid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Expense", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (submissionSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Split Successfully Synced!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Expense of ${String.format(Locale.US, "%.2f", totalAmount)} $currency has been added to Tricount.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Done")
                        }
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Concept & Amount Fields
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Merchant / Concept") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(2f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Currency") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // Tricount Group Dropdown
            item {
                var expanded by remember { mutableStateOf(false) }
                val currentTricount = tricounts.find { it.id == selectedTricountId }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = currentTricount?.title ?: "Select Tricount Group",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tricount Group") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        tricounts.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.title) },
                                onClick = {
                                    selectedTricountId = t.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Payer Selector (Horizontal Chip Row)
            if (members.isNotEmpty()) {
                item {
                    Text(
                        text = "Who paid?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { m ->
                            val isSelected = m.uuid == selectedPayerUuid
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPayerUuid = m.uuid },
                                label = { Text(m.displayName) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Category Picker
            item {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORIES.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Split Mode Tabs
            item {
                Text(
                    text = "Split Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SplitMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SplitMode.entries.size),
                            onClick = { splitMode = mode },
                            selected = splitMode == mode
                        ) {
                            Text(
                                when (mode) {
                                    SplitMode.EQUAL -> "Equal"
                                    SplitMode.CUSTOM -> "Custom"
                                    SplitMode.WEIGHTS -> "Weights"
                                }
                            )
                        }
                    }
                }
            }

            // Split Allocations based on mode
            when (splitMode) {
                SplitMode.EQUAL -> {
                    items(members) { m ->
                        val isChecked = equalSelectedMembers[m.uuid] ?: true
                        val share = equalAllocations[m.uuid]?.amount ?: 0.0

                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            equalSelectedMembers[m.uuid] = checked
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = m.displayName, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    text = if (isChecked) "${String.format(Locale.US, "%.2f", share)} $currency" else "-",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                SplitMode.CUSTOM -> {
                    var sumCustom = 0.0
                    members.forEach { m ->
                        sumCustom += (customAmounts[m.uuid]?.toDoubleOrNull() ?: 0.0)
                    }
                    val diff = totalAmount - sumCustom

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (abs(diff) < 0.01) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (abs(diff) < 0.01) "Amounts balance perfectly ✓" else "Remaining to allocate: ${String.format(Locale.US, "%.2f", diff)} $currency",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(members) { m ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = m.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = customAmounts[m.uuid].orEmpty(),
                                    onValueChange = { customAmounts[m.uuid] = it },
                                    label = { Text(currency) },
                                    modifier = Modifier.width(120.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
                SplitMode.WEIGHTS -> {
                    items(members) { m ->
                        val weight = memberWeights[m.uuid] ?: 1
                        val share = weightedAllocs[m.uuid]?.amount ?: 0.0

                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = m.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f", share)} $currency",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (weight > 0) memberWeights[m.uuid] = weight - 1
                                        }
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                    }
                                    Text(
                                        text = "${weight}x",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            memberWeights[m.uuid] = weight + 1
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submit Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val tricountId = selectedTricountId
                        val payerUuid = selectedPayerUuid

                        if (tricountId == null) {
                            Toast.makeText(context, "Please select a Tricount group", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (payerUuid == null) {
                            Toast.makeText(context, "Please select who paid", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (totalAmount <= 0.0) {
                            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Determine allocations
                        val finalAllocations: List<SplitCalculator.Allocation> = when (splitMode) {
                            SplitMode.EQUAL -> {
                                val active = members.filter { equalSelectedMembers[it.uuid] == true }.map { it.uuid }
                                if (active.isEmpty()) {
                                    Toast.makeText(context, "Select at least one member", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                SplitCalculator.calculateEqualSplit(totalAmount, active)
                            }
                            SplitMode.CUSTOM -> {
                                var sum = 0.0
                                val list = members.mapNotNull { m ->
                                    val amt = customAmounts[m.uuid]?.toDoubleOrNull() ?: 0.0
                                    sum += amt
                                    if (amt > 0.0) SplitCalculator.Allocation(m.uuid, SplitCalculator.roundToTwoDecimals(amt)) else null
                                }
                                if (abs(sum - totalAmount) > 0.01) {
                                    Toast.makeText(context, "Allocations must equal total amount", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                list
                            }
                            SplitMode.WEIGHTS -> {
                                val weights = members.associate { it.uuid to (memberWeights[it.uuid] ?: 0) }
                                if (weights.values.sum() == 0) {
                                    Toast.makeText(context, "At least one weight must be greater than 0", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                SplitCalculator.calculateWeightedSplit(totalAmount, weights)
                            }
                        }

                        isSubmitting = true
                        coroutineScope.launch {
                            val res = viewModel.submitSplit(
                                captureId = captureId,
                                tricountId = tricountId,
                                description = description,
                                amount = totalAmount,
                                currency = currency,
                                payerUuid = payerUuid,
                                allocations = finalAllocations,
                                category = selectedCategory
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                submissionSuccess = true
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                                Toast.makeText(context, "Sync Failed: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isSubmitting && totalAmount > 0.0
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Confirm Split (${String.format(Locale.US, "%.2f", totalAmount)} $currency)")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
