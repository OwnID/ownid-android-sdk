package com.ownid.demo.gigya.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.ownid.demo.gigya.R
import com.ownid.demo.gigya.ui.fragment.CreateFragment
import com.ownid.demo.gigya.ui.fragment.LoginFragment
import com.ownid.sdk.exception.OwnIdUserError

class MainActivity : AppCompatActivity() {

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (gigya.isLoggedIn) {
            startActivity(Intent(this@MainActivity, UserActivity::class.java))
            finish()
        }

        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.vp_activity_main)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> LoginFragment()
                1 -> CreateFragment()
                else -> throw IllegalStateException("Wrong position: $position")
            }
        }

        TabLayoutMediator(findViewById(R.id.tl_activity_main), viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Log in"
                1 -> "Create Account"
                else -> throw IllegalStateException("Wrong position: $position")
            }
        }.attach()

        // Fix for https://issuetracker.google.com/u/0/issues/143095219
        ViewPager2ViewHeightAnimator().viewPager2 = viewPager
    }

    fun showError(throwable: Throwable?) {
        val message = when (throwable) {
            is OwnIdUserError -> "[${throwable.code}]\n${throwable.userMessage}"
            else -> throwable?.message ?: throwable?.cause?.message ?: "Unknown error"
        }
        showError(message)
    }

    fun showError(message: String) {
        runOnUiThread {
            Log.e(this.javaClass.simpleName, "showError: $message")

            Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).apply {
                setBackgroundTint(context.getColor(R.color.ownid_error))
            }.show()
        }
    }

    class ViewPager2ViewHeightAnimator {
        var viewPager2: ViewPager2? = null
            set(value) {
                if (field != value) {
                    field?.unregisterOnPageChangeCallback(onPageChangeCallback)
                    field = value
                    value?.registerOnPageChangeCallback(onPageChangeCallback)
                }
            }

        private val layoutManager: LinearLayoutManager?
            get() = (viewPager2?.getChildAt(0) as? RecyclerView)?.layoutManager as? LinearLayoutManager

        private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                recalculate(position, positionOffset)
            }
        }

        private fun recalculate(position: Int, positionOffset: Float = 0f) = layoutManager?.apply {
            val leftView = findViewByPosition(position) ?: return@apply
            val rightView = findViewByPosition(position + 1)
            viewPager2?.apply {
                val leftHeight = getMeasuredViewHeightFor(leftView)
                layoutParams = layoutParams.apply {
                    height = if (rightView != null) {
                        val rightHeight = getMeasuredViewHeightFor(rightView)
                        leftHeight + ((rightHeight - leftHeight) * positionOffset).toInt()
                    } else {
                        leftHeight
                    }
                }
                invalidate()
            }
        }

        private fun getMeasuredViewHeightFor(view: View): Int {
            val wMeasureSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
            val hMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(wMeasureSpec, hMeasureSpec)
            return view.measuredHeight
        }
    }

}