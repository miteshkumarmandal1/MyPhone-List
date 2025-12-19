package com.mkm.myphonelist

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mkm.myphonelist.ui.theme.MyPhoneListTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors


class MainActivity : ComponentActivity() {
    private val contactViewModel: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyPhoneListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContactApp(contactViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactApp(contactViewModel: ContactViewModel = viewModel()) {
    val context = LocalContext.current
    val contacts by contactViewModel.contacts.collectAsState()
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showEditContactDialog by remember { mutableStateOf<Pair<Contact, Boolean>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val importVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contactViewModel.importVcfFromUri(context, it)
        }
    }

    val exportVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri: Uri? ->
        uri?.let {
            contactViewModel.exportContactsToVcf(context, it)
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search Contacts") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = outlinedTextFieldColors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text("MKContact")
                    }
                },
                actions = {
                    if (!isSearchMode) {

                        // IMPORT VCF icon
                        IconButton(onClick = {
                            importVcfLauncher.launch(arrayOf("text/x-vcard"))
                        }) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Import VCF"
                            )
                        }

                        // EXPORT VCF icon
                        IconButton(onClick = {
                            // Ask user where to save .vcf
                            exportVcfLauncher.launch("contacts_export.vcf")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Export VCF"
                            )
                        }

                        // existing search icon
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        }
                    } else {
                        IconButton(onClick = {
                            isSearchMode = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Search"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )

        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddContactDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Contact")
            }
        },
        content = { padding ->

            Column(modifier = Modifier.padding(padding)) {
                val filteredContacts = if (searchQuery.isEmpty()) {
                    contacts
                } else {
                    contacts.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.phoneNumber.contains(searchQuery) ||
                                it.email.contains(searchQuery, ignoreCase = true) ||
                                it.tag.contains(searchQuery, ignoreCase = true)
                    }
                }

                LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                    items(filteredContacts.size) { index ->
                        val contact = filteredContacts[index]
                        ContactItem(
                            contact = contact,
                            onEdit = { showEditContactDialog = contact to true },
                            onDelete = { contactViewModel.deleteContact(contact) },
                            onCall = { phoneNumber ->
                                val intent =
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                                context.startActivity(intent)
                            },
                            onWhatsApp = { phoneNumber ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://wa.me/$phoneNumber")
                                    setPackage("com.whatsapp")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: PackageManager.NameNotFoundException) {
                                    // Handle case where WhatsApp is not installed
                                    Toast.makeText(
                                        context,
                                        "WhatsApp not installed.",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            },
                            onSaveToPhone = { contact ->
                                contactViewModel.saveContactToPhone(context, contact)
                            }
                        )
                    }
                }
            }
        }
    )

    if (showAddContactDialog) {
        AddEditContactDialog(
            onDismiss = { showAddContactDialog = false },
            onConfirm = { name, phoneNumber, email, tag ->
                contactViewModel.addContact(name, phoneNumber, email, tag)
                showAddContactDialog = false
            }
        )
    }

    showEditContactDialog?.let { (contact, show) ->
        if (show) {
            AddEditContactDialog(
                contact = contact,
                onDismiss = { showEditContactDialog = null },
                onConfirm = { name, phoneNumber, email, tag ->
                    contactViewModel.updateContact(
                        contact.copy(
                            name = name,
                            phoneNumber = phoneNumber,
                            email = email,
                            tag = tag
                        )
                    )
                    showEditContactDialog = null
                }
            )
        }
    }
    LaunchedEffect(key1 = context) {
        contactViewModel.handleSharedContactData(context)
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onSaveToPhone: (Contact) -> Unit

) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Name: ${contact.name}")
            Text(text = "Phone: ${contact.phoneNumber}")
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Email: ${contact.email}")
                Text(text = "Tag: ${contact.tag}")
                Row {
                    Button(onClick = { onEdit() }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(text = "Edit")
                    }
                    Button(onClick = { showDialog = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(text = "Delete")
                    }
                    Button(onClick = { onCall(contact.phoneNumber) }) {
                        Text(text = "Call")
                    }
                }
                Row {
                    Button(
                        onClick = { onWhatsApp(contact.phoneNumber) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(text = "WhatsApp")
                    }
                    Button(
                        onClick = { onSaveToPhone(contact) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(text = "Save to Phone")
                    }
                }
            }
        }

        if (showDialog) {
            DeleteConfirmationDialog(
                onDeleteConfirmed = { onDelete() },
                onDismiss = { showDialog = false }
            )
        }
    }
}

@Composable
fun AddEditContactDialog(
    contact: Contact? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phoneNumber by remember { mutableStateOf(contact?.phoneNumber ?: "") }
    var email by remember { mutableStateOf(contact?.email ?: "") }
    var tag by remember { mutableStateOf(contact?.tag ?: "") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = if (contact == null) "Add Contact" else "Edit Contact") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    )
                )
                TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                TextField(value = tag, onValueChange = { tag = it }, label = { Text("Tag") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phoneNumber, email, tag) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}
@Composable
fun DeleteConfirmationDialog(
    onDeleteConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Contact") },
        text = { Text("Are you sure you want to delete this contact?") },
        confirmButton = {
            Button(
                onClick = {
                    onDeleteConfirmed()
                    onDismiss()
                }
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

