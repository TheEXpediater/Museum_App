package android.net

class TestUri(private val rawValue: String) : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = rawValue.substringBefore("://", "content")
    override fun getSchemeSpecificPart(): String = rawValue.substringAfter("://", rawValue)
    override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart
    override fun getAuthority(): String? = rawValue.substringAfter("://", "").substringBefore("/", "").ifBlank { null }
    override fun getEncodedAuthority(): String? = authority
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = authority
    override fun getPort(): Int = -1
    override fun getPath(): String = rawValue.substringAfter("://", rawValue).substringAfter("/", "")
    override fun getEncodedPath(): String = path
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = path.split("/").filter { it.isNotBlank() }
    override fun getLastPathSegment(): String? = pathSegments.lastOrNull()
    override fun toString(): String = rawValue
    override fun buildUpon(): Builder = throw UnsupportedOperationException("TestUri does not build new URIs.")
    override fun equals(other: Any?): Boolean = other is TestUri && other.rawValue == rawValue
    override fun hashCode(): Int = rawValue.hashCode()
    override fun compareTo(other: Uri): Int = rawValue.compareTo(other.toString())
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: android.os.Parcel, flags: Int) = Unit
}
