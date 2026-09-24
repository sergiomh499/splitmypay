package com.splitmypay.app.data.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionInstallationRequest(
    @SerialName("app_installation_uuid")
    val appInstallationUuid: String,
    @SerialName("client_public_key")
    val clientPublicKey: String,
    @SerialName("device_description")
    val deviceDescription: String = "Android"
)

@Serializable
data class SessionResponse(
    @SerialName("Response")
    val response: List<SessionItemWrapper> = emptyList()
)

@Serializable
data class SessionItemWrapper(
    @SerialName("Id")
    val id: IdItem? = null,
    @SerialName("Token")
    val token: TokenItem? = null,
    @SerialName("UserPerson")
    val userPerson: UserPersonItem? = null
)

@Serializable
data class IdItem(
    val id: Long
)

@Serializable
data class TokenItem(
    val id: Long? = null,
    val token: String
)

@Serializable
data class UserPersonItem(
    val id: Long,
    @SerialName("display_name")
    val displayName: String? = null
)

// Sync models
@Serializable
data class RegistrySyncRequest(
    @SerialName("all_registry_active")
    val allRegistryActive: List<ActiveRegistryToken>,
    @SerialName("all_registry_archived")
    val allRegistryArchived: List<String> = emptyList(),
    @SerialName("all_registry_deleted")
    val allRegistryDeleted: List<String> = emptyList()
)

@Serializable
data class ActiveRegistryToken(
    @SerialName("public_identifier_token")
    val publicIdentifierToken: String
)

@Serializable
data class RegistrySyncResponse(
    @SerialName("Response")
    val response: List<RegistryWrapper> = emptyList()
)

@Serializable
data class RegistryWrapper(
    @SerialName("Registry")
    val registry: RegistryDetail? = null
)

@Serializable
data class RegistryDetail(
    val id: Long,
    val title: String,
    val currency: String,
    val category: String? = null,
    @SerialName("public_identifier_token")
    val publicIdentifierToken: String? = null,
    @SerialName("all_membership")
    val allMembership: List<MembershipWrapper> = emptyList()
)

@Serializable
data class MembershipWrapper(
    @SerialName("RegistryMembership")
    val registryMembership: RegistryMembershipDetail
)

@Serializable
data class RegistryMembershipDetail(
    val uuid: String,
    val alias: AliasDetail? = null,
    @SerialName("public_nick_name")
    val publicNickName: String? = null,
    val status: String? = null
) {
    val effectiveDisplayName: String
        get() = alias?.displayName?.takeIf { it.isNotBlank() }
            ?: publicNickName?.takeIf { it.isNotBlank() }
            ?: "Member"
}

@Serializable
data class AliasDetail(
    @SerialName("display_name")
    val displayName: String? = null
)

// Expense Creation
@Serializable
data class RegistryEntryRequest(
    val uuid: String,
    val description: String,
    val amount: AmountValue,
    @SerialName("membership_owned")
    val membershipOwned: String,
    val allocations: List<AllocationItem>,
    @SerialName("type_transaction")
    val typeTransaction: String = "NORMAL",
    val status: String = "ACTIVE",
    val date: String,
    val category: String = "OTHER"
)

@Serializable
data class AmountValue(
    val value: String,
    val currency: String
)

@Serializable
data class AllocationItem(
    @SerialName("membership_uuid")
    val membershipUuid: String,
    val amount: AmountValue,
    val type: String = "AMOUNT"
)

@Serializable
data class EntryCreatedResponse(
    @SerialName("Response")
    val response: List<EntryCreatedWrapper> = emptyList()
)

@Serializable
data class EntryCreatedWrapper(
    @SerialName("Id")
    val id: IdItem? = null
)
