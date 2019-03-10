package com.thp.rxpermissions2

import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.reactivex.subjects.PublishSubject

class RxPermissionsFragment : androidx.fragment.app.Fragment() {

    // Contains all the current permission requests.
    // Once granted or denied, they are removed from it.
    private val mSubjects = hashMapOf<String, PublishSubject<Permission>>()
    private var mLogging: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @TargetApi(Build.VERSION_CODES.M)
    internal fun requestPermissions(permissions: Array<String?>) {
        requestPermissions(permissions, PERMISSIONS_REQUEST_CODE)
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSIONS_REQUEST_CODE) return

        val shouldShowRequestPermissionRationale = BooleanArray(permissions.size)

        for (i in permissions.indices) {
            shouldShowRequestPermissionRationale[i] = shouldShowRequestPermissionRationale(permissions[i])
        }

        onRequestPermissionsResult(permissions, grantResults, shouldShowRequestPermissionRationale)
    }

    fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray, shouldShowRequestPermissionRationale: BooleanArray) {
        var i = 0
        val size = permissions.size
        while (i < size) {
            log("onRequestPermissionsResult  " + permissions[i])
            // Find the corresponding subject
            val subject = mSubjects[permissions[i]]
            if (subject == null) {
                // No subject found
                Log.e(RxPermissions.TAG, "RxPermissions.onRequestPermissionsResult invoked but didn't find the corresponding permission request.")
                return
            }
            mSubjects.remove(permissions[i])
            val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
            subject.onNext(Permission(permissions[i], granted, shouldShowRequestPermissionRationale[i]))
            subject.onComplete()
            i++
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    internal fun isGranted(permission: String): Boolean {
        val fragmentActivity = activity
                ?: throw IllegalStateException("This fragment must be attached to an activity.")
        return fragmentActivity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    @TargetApi(Build.VERSION_CODES.M)
    internal fun isRevoked(permission: String): Boolean {
        val fragmentActivity = activity
                ?: throw IllegalStateException("This fragment must be attached to an activity.")
        return fragmentActivity.packageManager.isPermissionRevokedByPolicy(permission, this.activity?.packageName!!)
    }

    fun setLogging(logging: Boolean) {
        mLogging = logging
    }

    fun getSubjectByPermission(permission: String): PublishSubject<Permission>? {
        return mSubjects[permission]
    }

    fun containsByPermission(permission: String): Boolean {
        return mSubjects.containsKey(permission)
    }

    fun setSubjectForPermission(permission: String, subject: PublishSubject<Permission>) {
        mSubjects[permission] = subject
    }

    internal fun log(message: String) {
        if (mLogging) {
            Log.d(RxPermissions.TAG, message)
        }
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 42
    }
}
