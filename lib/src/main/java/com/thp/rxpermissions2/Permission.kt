package com.thp.rxpermissions2

import io.reactivex.Observable
import io.reactivex.functions.Function
import io.reactivex.functions.Predicate

class Permission {
    val name: String
    val granted: Boolean
    val shouldShowRequestPermissionRationale: Boolean

    @JvmOverloads
    constructor(name: String, granted: Boolean, shouldShowRequestPermissionRationale: Boolean = false) {
        this.name = name
        this.granted = granted
        this.shouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale
    }

    constructor(permissions: List<Permission>) {
        name = combineName(permissions)
        granted = combineGranted(permissions)
        shouldShowRequestPermissionRationale = combineShouldShowRequestPermissionRationale(permissions)
    }

    @SuppressWarnings("SimplifiableIfStatement")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass !== other.javaClass) return false

        val that = other as Permission?

        if (granted != that!!.granted) return false
        return if (shouldShowRequestPermissionRationale != that.shouldShowRequestPermissionRationale) false else name.equals(that.name)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + if (granted) 1 else 0
        result = 31 * result + if (shouldShowRequestPermissionRationale) 1 else 0
        return result
    }

    override fun toString(): String {
        return "Permission{" +
                "name='" + name + '\''.toString() +
                ", granted=" + granted +
                ", shouldShowRequestPermissionRationale=" + shouldShowRequestPermissionRationale +
                '}'.toString()
    }

    private fun combineName(permissions: List<Permission>): String {
        return Observable.fromIterable(permissions)
                .map(object : Function<Permission, String> {
                    @Throws(Exception::class)
                    override fun apply(permission: Permission): String {
                        return permission.name
                    }
                }).collectInto(StringBuilder()) { s, s2 ->
                    if (s.isEmpty()) {
                        s.append(s2)
                    } else {
                        s.append(", ").append(s2)
                    }
                }.blockingGet().toString()
    }

    private fun combineGranted(permissions: List<Permission>): Boolean {
        return Observable.fromIterable(permissions)
                .all(object : Predicate<Permission> {
                    @Throws(Exception::class)
                    override fun test(permission: Permission): Boolean {
                        return permission.granted
                    }
                }).blockingGet()
    }

    private fun combineShouldShowRequestPermissionRationale(permissions: List<Permission>): Boolean {
        return Observable.fromIterable(permissions)
                .any(object : Predicate<Permission> {
                    @Throws(Exception::class)
                    override fun test(permission: Permission): Boolean {
                        return permission.shouldShowRequestPermissionRationale
                    }
                }).blockingGet()
    }
}
