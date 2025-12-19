package com.mkm.myphonelist


import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ezvcard.Ezvcard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ezvcard.VCard

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val contactDao = ContactDatabase.getDatabase(application).contactDao()
    val contacts: StateFlow<List<Contact>> = contactDao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addContact(name: String, phoneNumber: String, email: String, tag: String) {
        viewModelScope.launch {
            val newContact =
                Contact(name = name, phoneNumber = phoneNumber, email = email, tag = tag)
            contactDao.insertContact(newContact)
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.deleteContact(contact)
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.updateContact(contact)
        }
    }


    fun extractContactInfo(sharedText: String): ContactInfo? {
        val lines = sharedText.lines()
        var name: String? = null
        var phoneNumber: String? = null
        var email: String = ""

        for (line in lines) {
            when {
                line.startsWith("[Name]") -> name = line.removePrefix("[Name]").trim()
                line.startsWith("[Mobile]") -> phoneNumber = line.removePrefix("[Mobile]").trim()
                line.startsWith("[Home]") -> email = line.removePrefix("[Home]").trim()
            }
        }

        return if (name != null && phoneNumber != null) {
            ContactInfo(name = name, phoneNumber = phoneNumber, email = email)
        } else {
            null
        }
    }

    fun handleSharedContactData(context: Context) {
        val receivedIntent = (context as Activity).intent
        val receivedAction = receivedIntent.action
        val receivedType = receivedIntent.type

        if (receivedAction == Intent.ACTION_SEND && receivedType != null) {
            when {
                receivedType == "text/plain" -> {
                    val sharedText = receivedIntent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedText?.let { text ->
                        // Extract contact information from shared text
                        val contactInfo = extractContactInfo(text)
                        if (contactInfo != null) {
                            // Add contact to database
                            addContact(
                                contactInfo.name,
                                contactInfo.phoneNumber,
                                contactInfo.email,
                                contactInfo.tag
                            )
                            Toast.makeText(context, "Contact added", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                receivedType == "text/x-vcard" -> {
                    val uri = receivedIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let {
                        val inputStream = context.contentResolver.openInputStream(it)
                        inputStream?.let { stream ->
                            val vCards = Ezvcard.parse(stream).all()
                            vCards.forEach { vCard ->
                                val name = vCard.formattedName?.value ?: ""
                                val phoneNumber = vCard.telephoneNumbers.firstOrNull()?.text ?: ""
                                val email = vCard.emails.firstOrNull()?.value ?: ""
                                if (name.isNotEmpty() && phoneNumber.isNotEmpty()) {
                                    addContact(name, phoneNumber, email, "")
                                }
                            }
                            Toast.makeText(context, "Contacts added from VCF", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }
    }

    fun saveContactToPhone(context: Context, contact: Contact) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
            putExtra(ContactsContract.Intents.Insert.PHONE, contact.phoneNumber)
            putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
            // You can add more fields here if needed
        }

        try {
            context.startActivity(intent)
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(context, "No app found to save contact", Toast.LENGTH_SHORT).show()
        }
    }

    fun importVcfFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                contentResolver.openInputStream(uri)?.use { stream ->
                    val vCards = Ezvcard.parse(stream).all()
                    vCards.forEach { vCard ->
                        val name = vCard.formattedName?.value ?: ""
                        val phoneNumber = vCard.telephoneNumbers.firstOrNull()?.text ?: ""
                        val email = vCard.emails.firstOrNull()?.value ?: ""
                        if (name.isNotEmpty() && phoneNumber.isNotEmpty()) {
                            val contact = Contact(
                                name = name,
                                phoneNumber = phoneNumber,
                                email = email,
                                tag = ""   // or some default
                            )
                            contactDao.insertContact(contact)
                        }
                    }
                }
                Toast.makeText(context, "VCF imported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to import VCF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportContactsToVcf(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val contacts = contactDao.getAllContactsOnce()

                if (contacts.isEmpty()) {
                    Toast.makeText(context, "No contacts to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val vCards = contacts.map { contact ->
                    VCard().apply {
                        formattedName = ezvcard.property.FormattedName(contact.name)
                        addTelephoneNumber(contact.phoneNumber)
                        if (contact.email.isNotEmpty()) {
                            addEmail(contact.email)
                        }
                    }
                }

                val contentResolver = context.contentResolver
                contentResolver.openOutputStream(uri)?.use { outStream ->
                    Ezvcard.write(vCards).go(outStream)
                }

                Toast.makeText(context, "VCF saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to export VCF", Toast.LENGTH_SHORT).show()
            }
        }
    }

}


data class ContactInfo(
    val name: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val tag: String = "" // Assuming tag is optional and can be empty
)

