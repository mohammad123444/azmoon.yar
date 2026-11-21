package android.net
class Uri(private val s: String) {
    val lastPathSegment: String? get() = s.substringAfterLast('/')
    val scheme: String? get() = s.substringBefore(':', "")
    override fun toString() = s
    override fun equals(other: Any?) = other is Uri && other.s == s
    override fun hashCode() = s.hashCode()
    companion object { fun parse(s: String) = Uri(s) }
}
