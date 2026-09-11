package com.ourbloom.app.data.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BankAccountDetails(
    val accountNumber: String = "",
    val ifscCode: String = "",
    val accountHolderName: String = "",
    val bankName: String = "",
    val upiId: String = ""
)
