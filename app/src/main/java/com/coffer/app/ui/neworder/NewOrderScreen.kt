package com.coffer.app.ui.neworder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.domain.effectiveUnitPriceCents
import com.coffer.app.domain.formatCents
import com.coffer.app.domain.normalizeBarcode
import com.coffer.app.ui.components.BarcodeScannerDialog
import com.coffer.app.ui.components.ProductThumbnail
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject

private data class ItemDraft(
    val id: Int,
    val productId: Int? = null,
    val usingNewProduct: Boolean = false,
    val newProductName: String = "",
    val newProductBuyPrice: String = "",
    val newProductSellPrice: String = "",
    val qty: String = "",
    val listPrice: String = "",
    val discountPercent: String = "0"
)

private fun ItemDraft.totalCents(): Long {
    val qtyVal = qty.toIntOrNull() ?: 0
    val listVal = listPrice.toDoubleOrNull() ?: 0.0
    val discount = discountPercent.toIntOrNull()?.coerceIn(0, 100) ?: 0
    return effectiveUnitPriceCents(Math.round(listVal * 100), discount) * qtyVal
}

/** Serializes the item drafts to a JSON string so the in-progress order survives rotation/process death. */
private val ItemDraftListSaver = Saver<List<ItemDraft>, String>(
    save = { list ->
        JSONArray().apply {
            list.forEach { d ->
                put(
                    JSONObject()
                        .put("id", d.id)
                        .put("productId", d.productId ?: JSONObject.NULL)
                        .put("usingNewProduct", d.usingNewProduct)
                        .put("newProductName", d.newProductName)
                        .put("newProductBuyPrice", d.newProductBuyPrice)
                        .put("newProductSellPrice", d.newProductSellPrice)
                        .put("qty", d.qty)
                        .put("listPrice", d.listPrice)
                        .put("discountPercent", d.discountPercent)
                )
            }
        }.toString()
    },
    restore = { json ->
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ItemDraft(
                id = o.getInt("id"),
                productId = if (o.isNull("productId")) null else o.getInt("productId"),
                usingNewProduct = o.getBoolean("usingNewProduct"),
                newProductName = o.getString("newProductName"),
                newProductBuyPrice = o.getString("newProductBuyPrice"),
                newProductSellPrice = o.getString("newProductSellPrice"),
                qty = o.getString("qty"),
                listPrice = o.getString("listPrice"),
                discountPercent = o.getString("discountPercent")
            )
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    viewModel: NewOrderViewModel = hiltViewModel()
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val presetContact by viewModel.presetContact.collectAsState()
    val products by viewModel.products.collectAsState()

    var isPurchase by rememberSaveable { mutableStateOf(true) }
    var selectedContactId by rememberSaveable { mutableStateOf<Int?>(null) }
    var usingNewContact by rememberSaveable { mutableStateOf(false) }
    var newContactName by rememberSaveable { mutableStateOf("") }
    var itemized by rememberSaveable { mutableStateOf(false) }
    var totalAmountText by rememberSaveable { mutableStateOf("") }
    var descriptionText by rememberSaveable { mutableStateOf("") }
    var paymentNowText by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var nextItemId by rememberSaveable { mutableStateOf(1) }
    var items by rememberSaveable(stateSaver = ItemDraftListSaver) { mutableStateOf(listOf(ItemDraft(0))) }
    var scanTargetIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var scanNotFoundIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(presetContact) {
        presetContact?.let { contact ->
            isPurchase = contact.type == ContactType.SUPPLIER.name
            selectedContactId = contact.id
        }
    }
    LaunchedEffect(Unit) {
        viewModel.created.collectLatest { onSaved(it) }
    }

    val contactOptions = if (isPurchase) suppliers else clients
    val itemizedTotalCents = items.sumOf { it.totalCents() }

    fun selectProduct(index: Int, productId: Int) {
        val suggestion = viewModel.suggestedPriceFor(productId, selectedContactId, isPurchase)
        items = items.toMutableList().also {
            it[index] = it[index].copy(
                productId = productId,
                listPrice = String.format("%.2f", suggestion.listUnitPriceCents / 100.0),
                discountPercent = suggestion.discountPercent.toString()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New order") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isPurchase,
                    onClick = { isPurchase = true; selectedContactId = null; usingNewContact = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("From a supplier") }
                SegmentedButton(
                    selected = !isPurchase,
                    onClick = { isPurchase = false; selectedContactId = null; usingNewContact = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("To a client") }
            }

            Column {
                Text("Contact", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    contactOptions.forEach { contact ->
                        FilterChip(
                            selected = selectedContactId == contact.id,
                            onClick = { selectedContactId = contact.id; usingNewContact = false },
                            label = { Text(contact.name) }
                        )
                    }
                    FilterChip(
                        selected = usingNewContact,
                        onClick = { usingNewContact = true; selectedContactId = null },
                        label = { Text("+ New") }
                    )
                }
                if (usingNewContact) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newContactName,
                            onValueChange = { newContactName = it },
                            label = { Text("New contact name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.createContact(newContactName, isPurchase) { newId ->
                                    selectedContactId = newId
                                    usingNewContact = false
                                    newContactName = ""
                                }
                            },
                            enabled = newContactName.isNotBlank()
                        ) { Text("Add") }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !itemized,
                    onClick = { itemized = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Total amount") }
                SegmentedButton(
                    selected = itemized,
                    onClick = { itemized = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Itemized") }
            }

            if (!itemized) {
                OutlinedTextField(
                    value = totalAmountText,
                    onValueChange = { totalAmountText = it },
                    label = { Text("Total amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEachIndexed { index, draft ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Item ${index + 1}", style = MaterialTheme.typography.labelMedium)
                                    IconButton(onClick = {
                                        items = items.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(ItemDraft(nextItemId++)) }
                                    }) { Icon(Icons.Default.Close, contentDescription = "Remove item") }
                                }

                                val selectedProduct = products.find { it.id == draft.productId }
                                if (selectedProduct == null && !draft.usingNewProduct) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        products.forEach { product ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { selectProduct(index, product.id) },
                                                label = { Text(product.name) },
                                                leadingIcon = if (product.photoPath != null) {
                                                    { ProductThumbnail(photoPath = product.photoPath, modifier = Modifier.size(20.dp)) }
                                                } else null
                                            )
                                        }
                                        FilterChip(
                                            selected = false,
                                            onClick = { items = items.toMutableList().also { it[index] = it[index].copy(usingNewProduct = true) } },
                                            label = { Text("+ New product") }
                                        )
                                        FilterChip(
                                            selected = false,
                                            onClick = { scanNotFoundIndex = null; scanTargetIndex = index },
                                            label = { Text("Scan") },
                                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                                        )
                                    }
                                    if (scanNotFoundIndex == index) {
                                        Text(
                                            "No product with that barcode.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else if (draft.usingNewProduct) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draft.newProductName,
                                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(newProductName = v) } },
                                            label = { Text("Product name") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = draft.newProductBuyPrice,
                                                onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(newProductBuyPrice = v) } },
                                                label = { Text("Buy price") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = draft.newProductSellPrice,
                                                onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(newProductSellPrice = v) } },
                                                label = { Text("Sell price") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }
                                        Button(
                                            enabled = draft.newProductName.isNotBlank() &&
                                                (draft.newProductBuyPrice.toDoubleOrNull() ?: 0.0) > 0 &&
                                                (draft.newProductSellPrice.toDoubleOrNull() ?: 0.0) > 0,
                                            onClick = {
                                                val buyCents = Math.round((draft.newProductBuyPrice.toDoubleOrNull() ?: 0.0) * 100)
                                                val sellCents = Math.round((draft.newProductSellPrice.toDoubleOrNull() ?: 0.0) * 100)
                                                viewModel.createProduct(draft.newProductName, buyCents, sellCents) { newProductId ->
                                                    val priceText = if (isPurchase) draft.newProductBuyPrice else draft.newProductSellPrice
                                                    items = items.toMutableList().also {
                                                        it[index] = it[index].copy(
                                                            productId = newProductId,
                                                            usingNewProduct = false,
                                                            listPrice = priceText,
                                                            discountPercent = "0"
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Add") }
                                    }
                                } else if (selectedProduct != null) {
                                    Text(selectedProduct.name, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draft.qty,
                                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(qty = v) } },
                                            label = { Text("Qty") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = draft.listPrice,
                                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(listPrice = v) } },
                                            label = { Text("List price") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = draft.discountPercent,
                                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(discountPercent = v) } },
                                            label = { Text("Discount %") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                    Text(
                                        "→ ${formatCents(draft.totalCents())} total",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { items = items + ItemDraft(nextItemId++) }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Add item")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Order total", style = MaterialTheme.typography.bodyMedium)
                        Text(formatCents(itemizedTotalCents), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            OutlinedTextField(
                value = paymentNowText,
                onValueChange = { paymentNowText = it },
                label = { Text("Payment now (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    viewModel.submit(
                        isPurchase = isPurchase,
                        existingContactId = selectedContactId,
                        newContactName = newContactName,
                        itemized = itemized,
                        totalAmountText = totalAmountText,
                        description = descriptionText,
                        items = items.mapNotNull { draft ->
                            draft.productId?.let { pid -> ItemEntry(pid, draft.qty, draft.listPrice, draft.discountPercent) }
                        },
                        paymentNowText = paymentNowText,
                        onError = { errorText = it }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save order") }
        }
    }

    scanTargetIndex?.let { index ->
        BarcodeScannerDialog(
            onDismiss = { scanTargetIndex = null },
            onScanned = { value ->
                val product = products.find { it.barcode == normalizeBarcode(value) }
                if (product != null) {
                    selectProduct(index, product.id)
                    scanNotFoundIndex = null
                } else {
                    scanNotFoundIndex = index
                }
                scanTargetIndex = null
            }
        )
    }
}
