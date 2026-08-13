-- 词、句逐字指定发音。
-- pinyins 保存于 words / sentences 的 JSONB 项中，格式示例：
-- {"text":"组长","pinyins":["zu","zhang"],"audio_url":"…"}。
-- 拼音不保存声调；腾讯评测发送时才临时补格式所需调号，并关闭声调检测。
-- 旧内容不回填，客户端会继续使用腾讯内置词典评测，避免影响既有学习卡。

comment on column public.child_literacy_characters.words is
    '词语 JSON 数组；每项含 text、pinyins（逐汉字无声调拼音）及可选音频字段';

comment on column public.child_literacy_characters.sentences is
    '句子 JSON 数组；每项含 text、pinyins（逐汉字无声调拼音）及可选音频字段';

comment on column public.recognized_characters.words is
    '词语 JSON 数组；每项含 text、pinyins（逐汉字无声调拼音）及可选音频字段';

comment on column public.recognized_characters.sentences is
    '句子 JSON 数组；每项含 text、pinyins（逐汉字无声调拼音）及可选音频字段';
