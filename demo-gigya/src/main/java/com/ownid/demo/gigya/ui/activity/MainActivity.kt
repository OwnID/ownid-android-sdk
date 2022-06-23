package com.ownid.demo.gigya.ui.activity

import androidx.fragment.app.Fragment
import com.ownid.demo.gigya.ui.fragment.CreateFragment
import com.ownid.demo.gigya.ui.fragment.LoginFragment
import com.ownid.demo.ui.activity.BaseMainActivity
import com.ownid.sdk.OwnId
import com.ownid.sdk.gigya

class MainActivity : BaseMainActivity() {

    override val serverUrl: String by lazy(LazyThreadSafetyMode.NONE) { OwnId.gigya.configuration.serverUrl.toString() }

    override fun getLoginFragment(): Fragment = LoginFragment()

    override fun getCreateFragment(): Fragment = CreateFragment()
}