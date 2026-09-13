package com.ourbloom.admin.data.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BankAccountDetails(
    val accountHolderName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val bankName: String = "",
    val upiId: String = ""
)
