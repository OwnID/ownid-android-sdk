package com.ownid.demo.gigya.screen.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

public object SignInWithGoogleButtonDefaults {

    @Composable
    public fun shape(): Shape = ShapeDefaults.Pill

    @Composable
    public fun buttonColors(): ButtonColors = if (isSystemInDarkTheme()) DarkButtonColors else LightButtonColors

    @Composable
    public fun borderStroke(): BorderStroke = BorderStroke(
        width = 1.dp,
        color = if (isSystemInDarkTheme()) ColorDefaults.Dark.BorderColor else ColorDefaults.Light.BorderColor
    )

    public object ShapeDefaults {
        public val Pill: Shape = RoundedCornerShape(50)
        public val Rectangular: Shape = RoundedCornerShape(4.0.dp)
    }

    public object ColorDefaults {
        public object Light {
            public val ContainerColor: Color = Color(0xFFFFFFFF)
            public val BorderColor: Color = Color(0xFF747775)
            public val ContentColor: Color = Color(0xFF1F1F1F)
        }

        public object Dark {
            public val ContainerColor: Color = Color(0xFF131314)
            public val BorderColor: Color = Color(0xFF8E918F)
            public val ContentColor: Color = Color(0xFFE3E3E3)
        }

        public object Neutral {
            public val ContainerColor: Color = Color(0xFFF2F2F2)
            public val BorderColor: Color = Color.Unspecified
            public val ContentColor: Color = Color(0xFF1F1F1F)
        }
    }

    public const val DisabledContainerAlpha: Float = 0.12f
    public const val DisabledContentAlpha: Float = 0.38f

    public val LightButtonColors: ButtonColors = ButtonColors(
        containerColor = ColorDefaults.Light.ContainerColor,
        contentColor = ColorDefaults.Light.ContentColor,
        disabledContainerColor = ColorDefaults.Light.ContainerColor.copy(alpha = DisabledContainerAlpha),
        disabledContentColor = ColorDefaults.Light.ContentColor.copy(alpha = DisabledContentAlpha)
    )

    public val DarkButtonColors: ButtonColors = ButtonColors(
        containerColor = ColorDefaults.Dark.ContainerColor,
        contentColor = ColorDefaults.Dark.ContentColor,
        disabledContainerColor = ColorDefaults.Dark.ContainerColor.copy(alpha = DisabledContainerAlpha),
        disabledContentColor = ColorDefaults.Dark.ContainerColor.copy(alpha = DisabledContentAlpha)
    )
}

// https://developers.google.com/identity/branding-guidelines
@Composable
public fun SignInWithGoogleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Sign in with Google",
    enabled: Boolean = true,
    shape: Shape = SignInWithGoogleButtonDefaults.shape(),
    colors: ButtonColors = SignInWithGoogleButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = SignInWithGoogleButtonDefaults.borderStroke(),
    fontFamily: FontFamily? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        interactionSource = interactionSource
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        Icon(
            painter = painterResource(id = com.ownid.demo.gigya.R.drawable.google_logo_24dp),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = null
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp),
            fontSize = 14.sp,
            fontFamily = fontFamily,
            lineHeight = 20.sp
        )
    }
}