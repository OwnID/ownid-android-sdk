package com.ownid.demo.integration.ui.activity

import androidx.fragment.app.Fragment
import com.ownid.demo.integration.ui.fragment.CreateFragment
import com.ownid.demo.integration.ui.fragment.LoginFragment
import com.ownid.demo.ui.activity.BaseMainActivity

class MainActivity : BaseMainActivity() {

    override fun getLoginFragment(): Fragment = LoginFragment()

    override fun getCreateFragment(): Fragment = CreateFragment()
}