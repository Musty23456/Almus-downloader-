package com.almus.downloader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.almus.downloader.R
import com.almus.downloader.model.ChatMessage
import com.almus.downloader.ui.components.AlmusTextField
import com.almus.downloader.ui.components.ContentContainer
import com.almus.downloader.ui.components.NoticeBanner
import com.almus.downloader.ui.components.ScreenHeader

/**
 * Chat layout for the future AI assistant. Nothing is connected: every reply is a fixed
 * local message that says so, and no text leaves the device.
 */
@Composable
fun AiAssistantScreen(modifier: Modifier = Modifier) {
    val greeting = stringResource(R.string.ai_greeting)
    val notConnectedReply = stringResource(R.string.ai_reply)

    val messages = remember {
        mutableStateListOf(ChatMessage(id = 0, text = greeting, fromUser = false))
    }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        messages.add(ChatMessage(id = messages.size, text = text, fromUser = true))
        messages.add(ChatMessage(id = messages.size, text = notConnectedReply, fromUser = false))
        input = ""
    }

    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.lastIndex)
    }

    ContentContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            ScreenHeader(
                title = stringResource(R.string.ai_title),
                subtitle = stringResource(R.string.ai_subtitle),
            )
            NoticeBanner(
                text = stringResource(R.string.ai_notice),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = messages, key = { it.id }) { message ->
                    MessageBubble(message = message, modifier = Modifier.animateItem())
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AlmusTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = stringResource(R.string.ai_input_label),
                    singleLine = false,
                    maxLines = 4,
                    imeAction = ImeAction.Send,
                    onImeAction = { send() },
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { send() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ai_send),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = if (message.fromUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    val sender = if (message.fromUser) {
        stringResource(R.string.ai_sender_you)
    } else {
        stringResource(R.string.ai_sender_assistant)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = sender,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Surface(
            shape = shape,
            color = if (message.fromUser) scheme.primaryContainer else scheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.fromUser) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
