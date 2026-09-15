package com.coffer.app.ui.products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.data.photo.ProductPhotoStore
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.components.BarcodeScannerDialog
import com.coffer.app.ui.components.CofferBottomBar
import com.coffer.app.ui.components.ProductThumbnail
import com.coffer.app.ui.navigation.Routes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onNavigate: (String) -> Unit,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProductRow?>(null) }
    var showPhotoSearchChooser by remember { mutableStateOf(false) }
    var pendingSearchCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSearchCameraFile by remember { mutableStateOf<File?>(null) }

    val searchTakePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingSearchCameraUri
        if (success && uri != null) {
            viewModel.searchByPhoto(context, uri, pendingSearchCameraFile)
        } else {
            pendingSearchCameraFile?.delete()
        }
    }
    val searchPickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.searchByPhoto(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products") },
                actions = {
                    IconButton(onClick = { showPhotoSearchChooser = true }) {
                        Icon(Icons.Default.ImageSearch, contentDescription = "Search by photo")
                    }
                }
            )
        },
        bottomBar = { CofferBottomBar(currentRoute = Routes.PRODUCTS, onNavigate = onNavigate) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New product")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.photoFilterActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Showing closest photo matches" +
                            if (uiState.photoFilterOmittedCount > 0) " · ${uiState.photoFilterOmittedCount} without a photo hidden" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.clearPhotoFilter() }) { Text("Clear") }
                }
            }
            if (uiState.rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (uiState.photoFilterActive) {
                            "None of your products have a photo yet — add one from a product's edit screen first."
                        } else {
                            "No products yet — tap + to add one, or create one while itemizing an order."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.rows, key = { it.product.id }) { row ->
                        ProductRowCard(row = row, onClick = { editTarget = row })
                    }
                }
            }
        }
    }

    if (showPhotoSearchChooser) {
        AlertDialog(
            onDismissRequest = { showPhotoSearchChooser = false },
            title = { Text("Search by photo") },
            text = { Text("Take or choose a photo of the item you're looking for. Products are shown closest match first.") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSearchChooser = false
                    val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "search_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    pendingSearchCameraUri = uri
                    pendingSearchCameraFile = file
                    searchTakePictureLauncher.launch(uri)
                }) { Text("Take a photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSearchChooser = false
                    searchPickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose from gallery") }
            }
        )
    }

    if (showAddDialog) {
        ProductDialog(
            title = "New product",
            initialName = "",
            initialBuyPrice = "",
            initialSellPrice = "",
            initialBarcode = "",
            initialPhotoPath = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, buyCents, sellCents, barcode, photoPath ->
                viewModel.createProduct(name, buyCents, sellCents, barcode, photoPath)
                showAddDialog = false
            },
            onDelete = null
        )
    }

    editTarget?.let { row ->
        ProductDialog(
            title = "Edit product",
            initialName = row.product.name,
            initialBuyPrice = String.format("%.2f", row.product.buyPriceCents / 100.0),
            initialSellPrice = String.format("%.2f", row.product.sellPriceCents / 100.0),
            initialBarcode = row.product.barcode ?: "",
            initialPhotoPath = row.product.photoPath,
            deleteBlockedMessage = if (!row.canDelete) "This product is used in an order and can't be deleted." else null,
            onDismiss = { editTarget = null },
            onSave = { name, buyCents, sellCents, barcode, photoPath ->
                viewModel.updateProduct(row.product, name, buyCents, sellCents, barcode, photoPath)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteProduct(row)
                editTarget = null
            }
        )
    }
}

@Composable
private fun ProductRowCard(row: ProductRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductThumbnail(photoPath = row.product.photoPath, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(row.product.name, fontWeight = FontWeight.Bold)
                    Text(
                        "Buy ${formatCents(row.product.buyPriceCents)} · Sell ${formatCents(row.product.sellPriceCents)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    row.product.barcode?.let { barcode ->
                        Text(barcode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${row.stockQuantity} in stock",
                    fontWeight = FontWeight.Bold,
                    color = if (row.stockQuantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ProductDialog(
    title: String,
    initialName: String,
    initialBuyPrice: String,
    initialSellPrice: String,
    initialBarcode: String,
    initialPhotoPath: String?,
    deleteBlockedMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, String?, String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var buyPrice by remember { mutableStateOf(initialBuyPrice) }
    var sellPrice by remember { mutableStateOf(initialSellPrice) }
    var barcode by remember { mutableStateOf(initialBarcode) }
    var photoPath by remember { mutableStateOf(initialPhotoPath) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun discardIfUnsaved(path: String?) {
        if (path != null && path != initialPhotoPath) ProductPhotoStore.deletePhoto(path)
    }

    fun setPhoto(newPath: String?) {
        discardIfUnsaved(photoPath)
        photoPath = newPath
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            ProductPhotoStore.savePhoto(context, uri)?.let { setPhoto(it) }
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ProductPhotoStore.savePhoto(context, uri)?.let { setPhoto(it) }
        }
    }

    AlertDialog(
        onDismissRequest = {
            discardIfUnsaved(photoPath)
            onDismiss()
        },
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductThumbnail(photoPath = photoPath, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row {
                            IconButton(onClick = {
                                val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "product_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                pendingCameraUri = uri
                                takePictureLauncher.launch(uri)
                            }) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Take photo")
                            }
                            IconButton(onClick = {
                                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Default.Photo, contentDescription = "Choose from gallery")
                            }
                            if (photoPath != null) {
                                IconButton(onClick = { setPhoto(null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove photo")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = buyPrice, onValueChange = { buyPrice = it }, label = { Text("Buy price (from supplier)") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = sellPrice, onValueChange = { sellPrice = it }, label = { Text("Sell price (to client)") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val buyValue = buyPrice.toDoubleOrNull()
                val sellValue = sellPrice.toDoubleOrNull()
                if (name.isBlank() || buyValue == null || buyValue <= 0 || sellValue == null || sellValue <= 0) {
                    error = "Enter a name and buy/sell prices greater than 0."
                } else {
                    onSave(name.trim(), Math.round(buyValue * 100), Math.round(sellValue * 100), barcode.trim().ifBlank { null }, photoPath)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (deleteBlockedMessage == null) onDelete() else error = deleteBlockedMessage }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = {
                    discardIfUnsaved(photoPath)
                    onDismiss()
                }) { Text("Cancel") }
            }
        }
    )

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { value ->
                barcode = value
                showScanner = false
            }
        )
    }
}
