package com.ourbloom.app.data.models

data class PaymentStatusDto(
    val txnid: String = "",
    val coupleId: String = "",
    val status: String = "",
    val vaultAmountPaise: Long? = null,
    val platformFeePaise: Long? = null,
    val payableAmountPaise: Long? = null,
    val netVaultCreditPaise: Long? = null,
    val pricingModel: String? = null
)
