/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thp.rxpermissions2

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import io.reactivex.Observable
import io.reactivex.Observable.empty
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.functions.Function
import io.reactivex.subjects.PublishSubject
import kotlin.collections.ArrayList

class RxPermissions {

    @VisibleForTesting
    internal var mRxPermissionsFragment: Lazy<RxPermissionsFragment>

    internal val isMarshmallow: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    constructor(activity: androidx.fragment.app.FragmentActivity) {
        mRxPermissionsFragment = getLazySingleton(activity.getSupportFragmentManager())
    }

    constructor(fragment: androidx.fragment.app.Fragment) {
        mRxPermissionsFragment = getLazySingleton(fragment.getChildFragmentManager())
    }

    private fun getLazySingleton(fragmentManager: androidx.fragment.app.FragmentManager): Lazy<RxPermissionsFragment> {
        return object : Lazy<RxPermissionsFragment> {

            var rxPermissionsFragment: RxPermissionsFragment? = null

            @Override
            @Synchronized
            override fun get(): RxPermissionsFragment? {
                if(rxPermissionsFragment == null) {
                    rxPermissionsFragment = getRxPermissionsFragment(fragmentManager)
                }
                return rxPermissionsFragment
            }

        }
    }

    private fun getRxPermissionsFragment(fragmentManager: androidx.fragment.app.FragmentManager): RxPermissionsFragment? {
        var rxPermissionsFragment: RxPermissionsFragment? = findRxPermissionsFragment(fragmentManager)
        val isNewInstance = rxPermissionsFragment == null
        if (isNewInstance) {
            rxPermissionsFragment = RxPermissionsFragment()
            fragmentManager
                    .beginTransaction()
                    .add(rxPermissionsFragment, TAG)
                    .commitNow()
        }
        return rxPermissionsFragment
    }

    private fun findRxPermissionsFragment(fragmentManager: androidx.fragment.app.FragmentManager): RxPermissionsFragment? {
        val fragment = fragmentManager.findFragmentByTag(TAG)
        return if(fragment!=null) fragment as RxPermissionsFragment else null
    }

    fun setLogging(logging: Boolean) {
        mRxPermissionsFragment.get()?.setLogging(logging)
    }

    /**
     * Map emitted items from the source observable into `true` if permissions in parameters
     * are granted, or `false` if not.
     *
     *
     * If one or several permissions have never been requested, invoke the related framework method
     * to ask the user if he allows the permissions.
     */
    @SuppressWarnings("WeakerAccess")
    fun <T> ensure(vararg permissions: String): ObservableTransformer<T, Boolean> {
        return object : ObservableTransformer<T, Boolean> {
            override fun apply(o: Observable<T>): ObservableSource<Boolean> {
                return request(o, *permissions)
                        .buffer(permissions.size)
                        .flatMap(object : Function<List<Permission>, ObservableSource<Boolean>> {
                            override fun apply(permissions: List<Permission>): ObservableSource<Boolean> {
                                if (permissions.isEmpty()) {
                                    // Occurs during orientation change, when the subject receives onComplete.
                                    // In that case we don't want to propagate that empty list to the
                                    // subscriber, only the onComplete.
                                    return empty()
                                }
                                // Return true if all permissions are granted.
                                for (p in permissions) {
                                    if (!p.granted) {
                                        return Observable.just(false)
                                    }
                                }
                                return Observable.just(true)
                            }
                        })
            }
        }
    }

    /**
     * Map emitted items from the source observable into [Permission] objects for each
     * permission in parameters.
     *
     *
     * If one or several permissions have never been requested, invoke the related framework method
     * to ask the user if he allows the permissions.
     */
    fun <T> ensureEach(vararg permissions: String): ObservableTransformer<T, Permission> {
        return object : ObservableTransformer<T, Permission> {
            override fun apply(o: Observable<T>): ObservableSource<Permission> {
                return request(o, *permissions)
            }
        }
    }

    /**
     * Map emitted items from the source observable into one combined [Permission] object. Only if all permissions are granted,
     * permission also will be granted. If any permission has `shouldShowRationale` checked, than result also has it checked.
     *
     *
     * If one or several permissions have never been requested, invoke the related framework method
     * to ask the user if he allows the permissions.
     */
    fun <T> ensureEachCombined(vararg permissions: String): ObservableTransformer<T, Permission> {
        return object : ObservableTransformer<T, Permission> {
            override fun apply(o: Observable<T>): ObservableSource<Permission> {
                return request(o, *permissions)
                        .buffer(permissions.size)
                        .flatMap(object : Function<List<Permission>, ObservableSource<Permission>> {
                            override fun apply(permissions: List<Permission>): ObservableSource<Permission> {
                                return if (permissions.isEmpty()) {
                                    empty()
                                } else Observable.just(Permission(permissions))
                            }
                        })
            }
        }
    }

    /**
     * Request permissions immediately, **must be invoked during initialization phase
     * of your application**.
     */
    @SuppressWarnings("WeakerAccess", "unused")
    fun request(vararg permissions: String): Observable<Boolean> {
        return Observable.just(TRIGGER).compose(ensure<Any>(*permissions))
    }

    /**
     * Request permissions immediately, **must be invoked during initialization phase
     * of your application**.
     */
    @SuppressWarnings("WeakerAccess", "unused")
    fun requestEach(vararg permissions: String): Observable<Permission> {
        return Observable.just(TRIGGER).compose(ensureEach<Any>(*permissions))
    }

    /**
     * Request permissions immediately, **must be invoked during initialization phase
     * of your application**.
     */
    fun requestEachCombined(vararg permissions: String): Observable<Permission> {
        return Observable.just(TRIGGER).compose(ensureEachCombined<Any>(*permissions))
    }

    private fun request(trigger: Observable<*>, vararg permissions: String): Observable<Permission> {
        if (permissions.isEmpty()) {
            throw IllegalArgumentException("RxPermissions.request/requestEach requires at least one input permission")
        }
        return oneOf(trigger, pending(*permissions))
                .flatMap(object : Function<Any, Observable<Permission>> {
                    override fun apply(o: Any): Observable<Permission> {
                        return requestImplementation(*permissions)
                    }
                })
    }

    private fun pending(vararg permissions: String): Observable<Any> {
        for (p in permissions) {
            if (mRxPermissionsFragment.get()?.containsByPermission(p)!!) {
                return empty()
            }
        }
        return Observable.just(TRIGGER)
    }

    private fun oneOf(trigger: Observable<*>?, pending: Observable<*>): Observable<*> {
        return if (trigger == null) {
            Observable.just(TRIGGER)
        } else Observable.merge(trigger, pending)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestImplementation(vararg permissions: String): Observable<Permission> {
        val list = ArrayList<Observable<Permission>>()
        val unrequestedPermissions = arrayListOf<Any>()

        // In case of multiple permissions, we create an Observable for each of them.
        // At the end, the observables are combined to have a unique response.
        for (permission in permissions) {
            mRxPermissionsFragment.get()?.log("Requesting permission $permission")
            if (isGranted(permission)) {
                // Already granted, or not Android M
                // Return a granted Permission object.
                list.add(Observable.just(Permission(permission, true, false)))
                continue
            }

            if (isRevoked(permission)) {
                // Revoked by a policy, return a denied Permission object.
                list.add(Observable.just(Permission(permission, false, false)))
                continue
            }

            var subject = mRxPermissionsFragment.get()?.getSubjectByPermission(permission)
            // Create a new subject if not exists
            if (subject == null) {
                unrequestedPermissions.add(permission)
                subject = PublishSubject.create()
                mRxPermissionsFragment.get()?.setSubjectForPermission(permission, subject)
            }

            list.add(subject)
        }

        if (!unrequestedPermissions.isEmpty()) {
            val unrequestedPermissionsArray = unrequestedPermissions.toArray(arrayOfNulls<String>(unrequestedPermissions.size))
            requestPermissionsFromFragment(unrequestedPermissionsArray)
        }
        return Observable.concat(Observable.fromIterable(list))
    }

    /**
     * Invokes Activity.shouldShowRequestPermissionRationale and wraps
     * the returned value in an observable.
     *
     *
     * In case of multiple permissions, only emits true if
     * Activity.shouldShowRequestPermissionRationale returned true for
     * all revoked permissions.
     *
     *
     * You shouldn't call this method if all permissions have been granted.
     *
     *
     * For SDK &lt; 23, the observable will always emit false.
     */
    @SuppressWarnings("WeakerAccess")
    fun shouldShowRequestPermissionRationale(activity: Activity, vararg permissions: String): Observable<Boolean> {
        return if (!isMarshmallow) {
            Observable.just(false)
        } else Observable.just(shouldShowRequestPermissionRationaleImplementation(activity, *permissions))
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun shouldShowRequestPermissionRationaleImplementation(activity: Activity, vararg permissions: String): Boolean {
        for (p in permissions) {
            if (!isGranted(p) && !activity.shouldShowRequestPermissionRationale(p)) {
                return false
            }
        }
        return true
    }

    @TargetApi(Build.VERSION_CODES.M)
    internal fun requestPermissionsFromFragment(permissions: Array<String?>) {
        mRxPermissionsFragment.get()?.log("requestPermissionsFromFragment " + TextUtils.join(", ", permissions))
        mRxPermissionsFragment.get()?.requestPermissions(permissions)
    }

    /**
     * Returns true if the permission is already granted.
     *
     *
     * Always true if SDK &lt; 23.
     */
    @SuppressWarnings("WeakerAccess")
    fun isGranted(permission: String): Boolean {
        return !isMarshmallow || mRxPermissionsFragment.get()!!.isGranted(permission)
    }

    /**
     * Returns true if the permission has been revoked by a policy.
     *
     *
     * Always false if SDK &lt; 23.
     */
    @SuppressWarnings("WeakerAccess")
    fun isRevoked(permission: String): Boolean {
        return isMarshmallow && mRxPermissionsFragment.get()!!.isRevoked(permission)
    }

    internal fun onRequestPermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        mRxPermissionsFragment.get()?.onRequestPermissionsResult(permissions, grantResults, BooleanArray(permissions.size))
    }

    @FunctionalInterface
    interface Lazy<V> {
        fun get(): V?
    }

    companion object {

        internal val TAG = RxPermissions::class.java!!.getSimpleName()
        internal val TRIGGER = Object()
    }

}
