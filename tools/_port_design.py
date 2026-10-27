p = 'ui/TaskScreen.kt'
s = open(p, encoding='utf-8').read()

# 1) User bubble -> right-aligned near-black, white text
old_u = '''                Surface(
                    color = Surface3,
                    shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = 15.dp, bottomEnd = 4.dp)
                ) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Ink
                    )
                }'''
new_u = '''                Surface(
                    color = Ink,
                    shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
                ) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = androidx.compose.ui.graphics.Color(0xFFF3F5F7)
                    )
                }'''
assert old_u in s, 'user bubble'
s = s.replace(old_u, new_u, 1)

# 2) Assistant meta row: muted small instead of bold
old_a = '''        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                message.modelName ?: "Agent",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )'''
new_a = '''        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                message.modelName ?: "Agent",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted2
            )'''
assert old_a in s, 'meta row'
s = s.replace(old_a, new_a, 1)

# 3) System branch: tool notes as cards, others keep amber notice
start = '    if (message.role == MessageRole.SYSTEM) {'
si = s.find(start)
assert si != -1, 'system branch start'
end_anchor = '        return\n    }\n    if (message.role == MessageRole.USER) {'
ei = s.find(end_anchor, si)
assert ei != -1, 'system branch end'

tool_card = '''    if (message.role == MessageRole.SYSTEM) {
        val lines = message.text.lines()
        val title = lines.firstOrNull().orEmpty()
        val body = lines.drop(1).joinToString("\\n").trim()
        if (title.startsWith("\\u2699")) {
            val label = title.removePrefix("\\u2699 ").trim()
            val ok = label.contains("\\u2192")
            val name = label.substringBefore("\\u2192").substringBefore("\\u2717").trim()
            val result = label.substringAfter("\\u2192", "").substringAfter("\\u2717", "").trim()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AdSurface,
                border = BorderStroke(1.dp, Line)
            ) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Build,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (ok) GreenInk else RedInk
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(name, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (ok) GreenSoft else RedSoft
                        ) {
                            Text(
                                if (ok) "Done" else "Failed",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ok) GreenInk else RedInk,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (result.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(result, fontSize = 10.sp, lineHeight = 14.sp, color = Muted, maxLines = 3)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(clockTime(message.at), fontSize = 8.5.sp, color = Muted2)
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AmberSoft,
                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEEDaAB))
            ) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = AmberInk)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AmberInk)
                        if (body.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(body, fontSize = 10.5.sp, lineHeight = 15.sp, color = AmberInk.copy(alpha = 0.88f))
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(clockTime(message.at), fontSize = 8.5.sp, color = AmberInk.copy(alpha = 0.65f))
                    }
                }
            }
        }
        return
    }
'''
s = s[:si] + tool_card + s[ei + len(end_anchor) - len('    if (message.role == MessageRole.USER) {'):]
open(p, 'w', encoding='utf-8').write(s)
print('all ported')
