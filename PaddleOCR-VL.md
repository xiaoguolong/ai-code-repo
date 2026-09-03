<script id="API_DOC_DATA" data-method="POST" type="application/json">{"debugUrl":{"url":"https://qianfan.baidubce.com/v2/ocr/paddleocr","href":"","method":"POST"},"debugDesc":"调用本接口，可根据用户输入的图像和文字，进行OCR文字识别。","authInfo":"## 权限说明\n\n调用本文API，需使用API Key鉴权方式。使用API Key鉴权调用API流程，具体调用流程，请查看 [认证鉴权](https://cloud.baidu.com/doc/qianfan-api/s/ym9chdsy5)。","requestCode":"POST /v2/ocr/paddleocr HTTP/1.1\nHost: qianfan.baidubce.com\nAuthorization: Bearer <API Key>\nContent-Type: application/json\n{\n    \"model\":\"paddleocr-vl-0.9b\",\n    \"file\": \"https://****.com/image1.jpg\",\n    \"fileType\": 1,\n    \"useChartRecognition\": true,\n    \"useDocUnwarping\":true,\n    \"useLayoutDetection\":true,\n    \"layoutNms\":true,\n    \"repetitionPenalty\":1.0,\n    \"temperature\":0,\n    \"topP\":1.0,\n    \"minPixels\":147384,\n    \"maxPixels\":2822400,\n    \"visualize\":true\n}    ","requestHeader":false,"requestParam":{"url":false,"body":{"type":"object","required":["model","file"],"properties":{"file":{"type":"string","description":"输入文件。<br>\n图像文件或PDF文件，支持URL或Base64<br>\n* 单pdf文档：大小控制50MB\n* 单图片image：大小控制10MB"},"topP":{"type":"float","description":"核采样阈值。<br>\n默认1.0，取值范围 [0, 1.0]<br>\n仅在累计概率达阈值的词集中采样，如 0.9 代表只考虑最可能的 90%。\n"},"model":{"type":"string","description":"大模型ID：固定值为`paddleocr-vl-0.9b`"},"fileType":{"type":"integer","description":"文件类型。<br>\n0表示PDF文件，1表示图像文件。<br>\n* 若`file`参数值为URL，可不传，将根据URL推断文件类型.\n* 若`file`参数值为base64，此字段为必传."},"layoutNms":{"type":"boolean","description":"NMS后处理。<br>\n开启后，会自动移除重复或高度重叠的区域框。"},"maxPixels":{"type":"integer","description":"动态分辨率上限。<br>\n默认2822400，取值范围： [1003520, 3211264]<br>\n预处理时若调整后总像素超过`maxPixels`的值会缩小到不超过该阈值。"},"minPixels":{"type":"integer","description":"动态分辨率下限。<br>\n默认147384，取值范围： [3136, 147384]<br>\n预处理时若调整后总像素低于`minPixels`的值会放大到不低于该阈值。\n"},"visualize":{"type":"boolean","description":"可视化。<br>\n控制本次请求是否返回可视化图像，如结果图或中间过程图。<br>\n默认值为 `true`。<br>\n* 传入`true`：返回图像。\n* 传入`false`：不返回图像。\n\n"},"promptLabel":{"type":"string","description":"prompt 的类型设置。<br>\n* 若开启版面分析（`useLayoutDetection`为 True），则系统将执行全面的版面识别，此时`promptLabel`参数的设置无效。\n* 若关闭版面分析（`useLayoutDetection`为 False），则必须通过`promptLabel`参数指定识别类型，其有效值为：`ocr`（文本）、`formula`（公式）、`table`（表格）或`chart`（图表）默认值为`ocr`。"},"temperature":{"type":"float","description":"控制随机性。<br>\n默认0.0，范围 [0, 2]<br>\n高值（如 0.8）更发散，低值（如 0.2）更确定，出现幻觉时可适当调高。"},"useDocUnwarping":{"type":"boolean","description":"图片扭曲矫正。<br>\n默认值为 false。<br>\n启用后，将自动检测并矫正图片中的文本区域形变，如褶皱和倾斜，为后续的识别步骤提供更规整的文本图像。"},"repetitionPenalty":{"type":"float","description":"控制重复惩罚。<br>\n默认1.0，取值范围：[1.0, 2.0]<br>\n控制模型生成重复内容的惩罚系数。值大于 1.0 会降低重复单词或短语出现的概率，值越高，惩罚越强，表格预测出现幻觉时可适当调高。\n"},"useLayoutDetection":{"type":"boolean","description":"版面分析。<br>\n默认值为 true。<br>\n开启后，将智能分析图片中的文档区域，如标题、段落，并按照正常的阅读顺序输出结果。"},"useChartRecognition":{"type":"boolean","description":"图表识别。<br>\n默认值为 false。<br>\n开启后，可以自动解析文档中的图表，如柱状图、饼图等，并转换为表格形式，方便查看和编辑数据。"},"useDocOrientationClassify":{"type":"boolean","description":"图片方向矫正。<br>\n默认值为 false。<br>\n启用后，将自动检测并校正图片的朝向。支持0°、90°、180°、270°旋转，以确保获得最佳的OCR效果。"}},"propertyOrder":["model","file","fileType","useDocOrientationClassify","useDocUnwarping","useLayoutDetection","useChartRecognition","layoutNms","promptLabel","repetitionPenalty","temperature","topP","minPixels","maxPixels","visualize"]},"query":false,"urlDesc":"","bodyDesc":"","queryDesc":""},"requestExampleCode":[{"scene":"请求示例","sceneDesc":"","languageTabs":[{"code":"curl https://qianfan.baidubce.com/v2/ocr/paddleocr \\\n  -H \"Content-Type: application/json\" \\\n  -H \"Authorization: Bearer <API Key>\" \\\n  -d '{\n    \"model\":\"paddleocr-vl-0.9b\",\n    \"file\": \"https://****.com/image1.jpg\",\n    \"fileType\": 1,\n    \"useChartRecognition\": true,\n    \"useDocUnwarping\":true,\n    \"useLayoutDetection\":true,\n    \"layoutNms\":true,\n    \"repetitionPenalty\":1.0,\n    \"temperature\":0,\n    \"topP\":1.0,\n    \"minPixels\":147384,\n    \"maxPixels\":2822400,\n    \"visualize\":true\n}'","lang":"","codeHtml":"<pre class=\"shiki github-dark-default\" style=\"background-color:#0d1117;color:#e6edf3\" tabindex=\"0\"><code><span class=\"line\"><span style=\"color:#FFA657\">curl</span><span style=\"color:#A5D6FF\"> https://qianfan.baidubce.com/v2/ocr/paddleocr</span><span style=\"color:#FF7B72\"> \\</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">  -H</span><span style=\"color:#A5D6FF\"> \"Content-Type: application/json\"</span><span style=\"color:#FF7B72\"> \\</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">  -H</span><span style=\"color:#A5D6FF\"> \"Authorization: Bearer &#x3C;API Key>\"</span><span style=\"color:#FF7B72\"> \\</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">  -d</span><span style=\"color:#A5D6FF\"> '{</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"model\":\"paddleocr-vl-0.9b\",</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"file\": \"https://****.com/image1.jpg\",</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"fileType\": 1,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"useChartRecognition\": true,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"useDocUnwarping\":true,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"useLayoutDetection\":true,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"layoutNms\":true,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"repetitionPenalty\":1.0,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"temperature\":0,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"topP\":1.0,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"minPixels\":147384,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"maxPixels\":2822400,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"visualize\":true</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">}'</span></span></code></pre>"}]}],"responseHeader":false,"responseBody":{"type":"object","required":["id","result"],"properties":{"id":{"type":"string","description":"本次请求的唯一标识，可用于排查问题。"},"result":{"type":"object","required":["datainfo"],"properties":{"datainfo":{"type":"object","required":["type"],"properties":{"type":{"type":"string","description":"输入文件类型，可为image或pdf"},"pages":{"type":"array","items":{"type":"object","properties":{}},"description":"每页详细信息，包括宽高。<br>\n当type=pdf时返回。"},"width":{"type":"integer","description":"图片宽度。<br>\n当type=image时返回。"},"height":{"type":"integer","description":"图片高度。<br>\n当type=image时返回。"},"numPages":{"type":"integer","description":"文件页数。<br>\n当type=pdf时返回。"}},"description":"输入数据信息","propertyOrder":["type","numPages","pages","width","height"]},"layoutParsingResults":{"type":"array","items":{"type":"object","required":["prunedResult","markdown"],"properties":{"markdown":{"type":"object","required":["text","images"],"properties":{"text":{"type":"string","description":"Markdown 文本。"},"isEnd":{"type":"string","description":"当前页面最后一个元素是否为段结束"},"images":{"type":"object","properties":{},"description":"图像键值对。<br>\nkey：图片相对路径。<br>\nvalue：对应的图片链接，为JPEG格式，有效期 30 天。","propertyOrder":[]},"isStart":{"type":"string","description":"当前页面第一个元素是否为段开始"}},"description":"Markdown 结果。","propertyOrder":["text","images","isStart","isEnd"]},"inputImage":{"type":"string","description":"输入图片链接，为JPEG格式，有效期 30 天。"},"outputImages":{"type":"object","properties":{},"description":"输出图片链接，为JPEG格式，有效期 30 天<br>\n返回的预测结果是一个dict类型的数据。其中，键分别为`layout_det_res`、`layout_order_res`、`layout_order_res`和 `preprocessed_img` ，对应的值是Image.Image对象。\n* `layout_det_res`：用于显示版面区域检测的可视化图像。\n* `layout_order_res`：用于显示版面阅读顺序结果的可视化图像。\n* `preprocessed_img`：用于展示图像预处理的可视化图像。","propertyOrder":[]},"prunedResult":{"type":"object","required":["model_settings","parsing_res_list"],"properties":{"layout_det_res":{"type":"object","required":["boxes"],"properties":{"boxes":{"type":"array","items":{"type":"object","required":["cls_id","label","socre","coordinate"],"properties":{"label":{"type":"string","description":"类别名称。"},"socre":{"type":"float","description":"目标框置信度。"},"cls_id":{"type":"integer","description":"类别 id。"},"coordinate":{"type":"array","items":{"type":"float"},"description":"目标框坐标，一个浮点数列表，格式为[xmin, ymin, xmax, ymax]。"}},"propertyOrder":["cls_id","label","socre","coordinate"]},"description":"预测的目标框信息，一个字典列表。每个字典代表一个检出的目标。"},"input_path":{"type":"string","description":"输入的待预测图像的路径。"},"page_index":{"type":"string","description":"如果输入是PDF文件，则表示当前是PDF的第几页。"}},"description":"版面区域检测排序结果。","propertyOrder":["boxes","input_path","page_index"]},"model_settings":{"type":"object","required":["use_doc_preprocessor","use_layout_detection","use_chart_recognition","format_block_content"],"properties":{"format_block_content":{"type":"boolean","description":"是否在JSON中保存格式化后的markdown内容。"},"use_doc_preprocessor":{"type":"boolean","description":"是否启用文档预处理子产线。"},"use_layout_detection":{"type":"boolean","description":"是否启用版面检测模块。"},"use_chart_recognition":{"type":"boolean","description":"是否开启图标识别功能。"}},"description":"配置 PaddleOCR-VL 所需的模型参数。","propertyOrder":["use_doc_preprocessor","use_layout_detection","use_chart_recognition","format_block_content"]},"parsing_res_list":{"type":"array","items":{"type":"object","required":["block_label","block_content","block_bbox","block_id","block_order"],"properties":{"block_id":{"type":"integer","description":"版面区域的索引，用于显示版面排序结果。<br>"},"block_bbox":{"type":"array","items":{"type":"integer"},"description":"内容为版面区域内的内容。"},"block_label":{"type":"string","description":"版面区域的边界框。"},"block_order":{"type":"integer","description":"版面区域的顺序，用于显示版面阅读顺序。<br>\n对于非排序部分，默认值为 null。"},"block_content":{"type":"string","description":"版面区域的标签，例如text, table等。"}},"propertyOrder":["block_label","block_content","block_bbox","block_id","block_order"]},"description":"解析结果的列表，每个元素为一个字典，列表顺序为解析后的阅读顺序。"}},"description":"处理后结果。<br>\n对象的predict方法生成结果的 JSON 表示中res字段的简化版本，其中去除了input_path和page_index字段。","propertyOrder":["model_settings","parsing_res_list","layout_det_res"]}},"propertyOrder":["prunedResult","markdown","outputImages","inputImage"]},"description":"版面解析结果。<br>\n数组长度为1（对于图像输入）或实际处理的文档页数（对于PDF输入）。对于PDF输入，数组中的每个元素依次表示PDF文件中实际处理的每一页的结果。"}},"propertyOrder":["layoutParsingResults","datainfo"],"description":"输出结果。"}},"propertyOrder":["id","result"]},"responseExampleCode":[{"scene":"正确响应示例","sceneDesc":"","languageTabs":[{"code":"{\n    \"id\": \"as-****yda9\",\n    \"result\": {\n        \"layoutParsingResults\": [\n            {\n                \"prunedResult\": {\n                    \"model_settings\": {\n                        \"use_doc_preprocessor\": true,\n                        \"use_layout_detection\": true,\n                        \"use_chart_recognition\": true,\n                        \"format_block_content\": false\n                    },\n                    \"parsing_res_list\": [\n                        {\n                            \"block_label\": \"image\",\n                            \"block_content\": \"\",\n                            \"block_bbox\": [\n                                0,\n                                2,\n                                800,\n                                531\n                            ],\n                            \"block_id\": 0,\n                            \"block_order\": null\n                        }\n                    ],\n                    \"layout_det_res\": {\n                        \"boxes\": [\n                            {\n                                \"cls_id\": 14,\n                                \"label\": \"image\",\n                                \"score\": 0.5476956963539124,\n                                \"coordinate\": [\n                                    0.01312255859375,\n                                    2.161956787109375,\n                                    800,\n                                    531\n                                ]\n                            }\n                        ]\n                    }\n                },\n                \"markdown\": {\n                    \"text\": \"<div style=\\\"text-align: center;\\\"><img src=\\\"imgs/img_in_image_box_0_2_800_531.jpg\\\" alt=\\\"Image\\\" width=\\\"100%\\\" /></div>\\n\",\n                    \"images\": {\n                        \"imgs/img_in_image_box_0_2_800_531.jpg\": \"http://***/ocr/paddleocr/a5917dda-fc8d-4406-7b71-d9220befe416.jpeg\"\n                    }\n                },\n                \"outputImages\": {\n                    \"layout_det_res\": \"http://***/ocr/paddleocr/76a8647c-1b22-4e1b-6f83-a44b5711b4b6.jpeg\",\n                    \"layout_order_res\": \"http://***/ocr/paddleocr/6043f57f-708c-4c7d-634e-e6124d1d7f11.jpeg\",\n                    \"preprocessed_img\": \"http://***/ocr/paddleocr/c7f92081-d403-4189-79c9-931f9883de03.jpeg\"\n                },\n                \"inputImage\": \"http://***/ocr/paddleocr/91f300cb-8e7a-43ae-544b-a683c5e52185.jpeg\"\n            }\n        ],\n        \"dataInfo\": {\n            \"type\": \"image\",\n            \"width\": 800,\n            \"height\": 531\n        }\n    }\n}","lang":"JSON","codeHtml":"<pre class=\"shiki github-dark-default\" style=\"background-color:#0d1117;color:#e6edf3\" tabindex=\"0\"><code><span class=\"line\"><span style=\"color:#E6EDF3\">{</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"id\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"as-****yda9\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"result\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">        \"layoutParsingResults\"</span><span style=\"color:#E6EDF3\">: [</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">            {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                \"prunedResult\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"model_settings\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                        \"use_doc_preprocessor\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">true</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                        \"use_layout_detection\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">true</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                        \"use_chart_recognition\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">true</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                        \"format_block_content\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">false</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                    },</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"parsing_res_list\"</span><span style=\"color:#E6EDF3\">: [</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                        {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                            \"block_label\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"image\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                            \"block_content\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                            \"block_bbox\"</span><span style=\"color:#E6EDF3\">: [</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                0</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                2</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                800</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                531</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                            ],</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                            \"block_id\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">0</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                            \"block_order\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">null</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                        }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                    ],</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"layout_det_res\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                        \"boxes\"</span><span style=\"color:#E6EDF3\">: [</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                            {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                                \"cls_id\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">14</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                                \"label\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"image\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                                \"score\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">0.5476956963539124</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                                \"coordinate\"</span><span style=\"color:#E6EDF3\">: [</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                    0.01312255859375</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                    2.161956787109375</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                    800</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#79C0FF\">                                    531</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                                ]</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                            }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                        ]</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                    }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                },</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                \"markdown\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"text\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"&#x3C;div style=</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\">text-align: center;</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\">>&#x3C;img src=</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\">imgs/img_in_image_box_0_2_800_531.jpg</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\"> alt=</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\">Image</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\"> width=</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\">100%</span><span style=\"color:#FF7B72\">\\\"</span><span style=\"color:#A5D6FF\"> />&#x3C;/div></span><span style=\"color:#FF7B72\">\\n</span><span style=\"color:#A5D6FF\">\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"images\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                        \"imgs/img_in_image_box_0_2_800_531.jpg\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"http://***/ocr/paddleocr/a5917dda-fc8d-4406-7b71-d9220befe416.jpeg\"</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                    }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                },</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                \"outputImages\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"layout_det_res\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"http://***/ocr/paddleocr/76a8647c-1b22-4e1b-6f83-a44b5711b4b6.jpeg\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"layout_order_res\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"http://***/ocr/paddleocr/6043f57f-708c-4c7d-634e-e6124d1d7f11.jpeg\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                    \"preprocessed_img\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"http://***/ocr/paddleocr/c7f92081-d403-4189-79c9-931f9883de03.jpeg\"</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">                },</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">                \"inputImage\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"http://***/ocr/paddleocr/91f300cb-8e7a-43ae-544b-a683c5e52185.jpeg\"</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">            }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">        ],</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">        \"dataInfo\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">            \"type\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"image\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">            \"width\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">800</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">            \"height\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#79C0FF\">531</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">        }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">    }</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">}</span></span></code></pre>"}]},{"scene":"错误响应示例","sceneDesc":"","languageTabs":[{"code":"{\n    \"error\": {\n        \"code\": \"invalid_argument\",\n        \"message\": \"fetch object failed\",\n        \"type\": \"invalid_request_error\"\n    },\n    \"id\": \"as-xpv8aftdfq\"\n}","lang":"JSON","codeHtml":"<pre class=\"shiki github-dark-default\" style=\"background-color:#0d1117;color:#e6edf3\" tabindex=\"0\"><code><span class=\"line\"><span style=\"color:#E6EDF3\">{</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"error\"</span><span style=\"color:#E6EDF3\">: {</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">        \"code\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"invalid_argument\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">        \"message\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"fetch object failed\"</span><span style=\"color:#E6EDF3\">,</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">        \"type\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"invalid_request_error\"</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">    },</span></span>\n<span class=\"line\"><span style=\"color:#A5D6FF\">    \"id\"</span><span style=\"color:#E6EDF3\">: </span><span style=\"color:#A5D6FF\">\"as-xpv8aftdfq\"</span></span>\n<span class=\"line\"><span style=\"color:#E6EDF3\">}</span></span></code></pre>"}]}],"otherContent":"## 错误码\n\n如果调用失败并返回报错信息，请参见错误码进行解决。\n\n公共错误码：[查看公共错误码](https://cloud.baidu.com/doc/qianfan-api/s/Om9b4yj3w)\n\n本接口专有错误码如下：\n\n| HTTP状态码 | 类型 | 错误码 | 错误信息 | 说明 |\n| ---       | --- | ---   | ---    | --- |\n| 500 | internal_error | ocr_internal_error | 返回的具体错误信息\t |  ocr内部错误   |\n| 400 | invalid_request_error | invalid_argument | 返回的具体错误信息\t |  参数报错   |\n| 401 | invalid_request_error | invalid_model | No permission to use the model |  model鉴权失败，该用户没有使用这个model的权限   |\n| 500 | invalid_request_error | invalid_model | Model is empty |  未指定model参数   |"}</script>
<main id="doc-body">
<section id="debug-url">
<div class="debug-wrapper">
<div class="debug-url-wrapper">
<div class="debug-url-method" data-method="post">POST</div>
<div class="debug-url-text" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="路径" data-track-value="https://qianfan.baidubce.com/v2/ocr/paddleocr" data-copy-text="https://qianfan.baidubce.com/v2/ocr/paddleocr">
<span>https://qianfan.baidubce.com/v2/ocr/paddleocr</span>
</div>
</div>
</div>
<p>调用本接口，可根据用户输入的图像和文字，进行OCR文字识别。</p>
</section>
<section id="auth-info">
<h2 id="权限说明">权限说明<a class="anchor" aria-label="权限说明 permalink" href="#%E6%9D%83%E9%99%90%E8%AF%B4%E6%98%8E"></a></h2>
<p>调用本文API，需使用API Key鉴权方式。使用API Key鉴权调用API流程，具体调用流程，请查看 <a href="https://cloud.baidu.com/doc/qianfan-api/s/ym9chdsy5" target="_blank">认证鉴权</a>。</p>
</section>
<section id="request-params">
<h2 class="section-title" id="请求参数">请求参数<a class="anchor" aria-label="请求参数 permalink" href="#%E8%AF%B7%E6%B1%82%E5%8F%82%E6%95%B0"></a></h2>
<div class="horizontal-container">
<div class="horizontal-left-panel">
<div class="param-card param-card-empty">
<div class="param-card-header">
<div class="param-card-title">Headers 参数</div>
<div class="param-card-header-desc">除公共头域外，无其它特殊头域</div>
</div>
</div>
<div class="param-card">
<div class="param-card-header">
<div class="param-card-title">Body 参数</div>
</div>
<div class="param-card-content">
<div class="api-param-list  ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="model" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="model">
model
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>大模型ID：固定值为<code>paddleocr-vl-0.9b</code></p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="file" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="file">
file
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>输入文件。<br>
图像文件或PDF文件，支持URL或Base64<br></p>
<ul>
<li>单pdf文档：大小控制50MB</li>
<li>单图片image：大小控制10MB</li>
</ul>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="fileType" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="fileType">
fileType
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>文件类型。<br>
0表示PDF文件，1表示图像文件。<br></p>
<ul>
<li>若<code>file</code>参数值为URL，可不传，将根据URL推断文件类型.</li>
<li>若<code>file</code>参数值为base64，此字段为必传.</li>
</ul>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="useDocOrientationClassify" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="useDocOrientationClassify">
useDocOrientationClassify
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>图片方向矫正。<br>
默认值为 false。<br>
启用后，将自动检测并校正图片的朝向。支持0°、90°、180°、270°旋转，以确保获得最佳的OCR效果。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="useDocUnwarping" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="useDocUnwarping">
useDocUnwarping
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>图片扭曲矫正。<br>
默认值为 false。<br>
启用后，将自动检测并矫正图片中的文本区域形变，如褶皱和倾斜，为后续的识别步骤提供更规整的文本图像。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="useLayoutDetection" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="useLayoutDetection">
useLayoutDetection
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>版面分析。<br>
默认值为 true。<br>
开启后，将智能分析图片中的文档区域，如标题、段落，并按照正常的阅读顺序输出结果。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="useChartRecognition" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="useChartRecognition">
useChartRecognition
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>图表识别。<br>
默认值为 false。<br>
开启后，可以自动解析文档中的图表，如柱状图、饼图等，并转换为表格形式，方便查看和编辑数据。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="layoutNms" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="layoutNms">
layoutNms
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>NMS后处理。<br>
开启后，会自动移除重复或高度重叠的区域框。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="promptLabel" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="promptLabel">
promptLabel
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>prompt 的类型设置。<br></p>
<ul>
<li>若开启版面分析（<code>useLayoutDetection</code>为 True），则系统将执行全面的版面识别，此时<code>promptLabel</code>参数的设置无效。</li>
<li>若关闭版面分析（<code>useLayoutDetection</code>为 False），则必须通过<code>promptLabel</code>参数指定识别类型，其有效值为：<code>ocr</code>（文本）、<code>formula</code>（公式）、<code>table</code>（表格）或<code>chart</code>（图表）默认值为<code>ocr</code>。</li>
</ul>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="repetitionPenalty" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="repetitionPenalty">
repetitionPenalty
</span>
<span class="api-param-list-item-type">float</span>
</div>
<div class="api-param-desc"><p>控制重复惩罚。<br>
默认1.0，取值范围：[1.0, 2.0]<br>
控制模型生成重复内容的惩罚系数。值大于 1.0 会降低重复单词或短语出现的概率，值越高，惩罚越强，表格预测出现幻觉时可适当调高。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="temperature" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="temperature">
temperature
</span>
<span class="api-param-list-item-type">float</span>
</div>
<div class="api-param-desc"><p>控制随机性。<br>
默认0.0，范围 [0, 2]<br>
高值（如 0.8）更发散，低值（如 0.2）更确定，出现幻觉时可适当调高。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="topP" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="topP">
topP
</span>
<span class="api-param-list-item-type">float</span>
</div>
<div class="api-param-desc"><p>核采样阈值。<br>
默认1.0，取值范围 [0, 1.0]<br>
仅在累计概率达阈值的词集中采样，如 0.9 代表只考虑最可能的 90%。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="minPixels" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="minPixels">
minPixels
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>动态分辨率下限。<br>
默认147384，取值范围： [3136, 147384]<br>
预处理时若调整后总像素低于<code>minPixels</code>的值会放大到不低于该阈值。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="maxPixels" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="maxPixels">
maxPixels
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>动态分辨率上限。<br>
默认2822400，取值范围： [1003520, 3211264]<br>
预处理时若调整后总像素超过<code>maxPixels</code>的值会缩小到不超过该阈值。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="visualize" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="visualize">
visualize
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>可视化。<br>
控制本次请求是否返回可视化图像，如结果图或中间过程图。<br>
默认值为 <code>true</code>。<br></p>
<ul>
<li>传入<code>true</code>：返回图像。</li>
<li>传入<code>false</code>：不返回图像。</li>
</ul>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
</div>
</div>
</div>
</div>
<div class="horizontal-right-panel">
<div class="code-block request-code-block">
<div class="code-block-header">
<div class="code-block-name ">请求结构</div>
<button class="code-copy-btn" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="请求结构 代码" data-track-value="复制" data-tooltip-text="">
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none"> <path fill-rule="evenodd" clip-rule="evenodd" d="M5.57894 3.45614C5.57894 3.38832 5.63392 3.33333 5.70175 3.33333H12.5439C12.6117 3.33333 12.6667 3.38832 12.6667 3.45614V10.2982C12.6667 10.3661 12.6117 10.4211 12.5439 10.4211H11.7544V5.70175C11.7544 4.89754 11.1025 4.24561 10.2982 4.24561H5.57894V3.45614ZM4.24561 4.24561V3.45614C4.24561 2.65194 4.89754 2 5.70175 2H12.5439C13.3481 2 14 2.65194 14 3.45614V10.2982C14 11.1025 13.3481 11.7544 12.5439 11.7544H11.7544V12.5439C11.7544 13.3481 11.1025 14 10.2982 14H3.45614C2.65194 14 2 13.3481 2 12.5439V5.70175C2 4.89754 2.65194 4.24561 3.45614 4.24561H4.24561ZM3.33333 5.70175C3.33333 5.63392 3.38832 5.57894 3.45614 5.57894H10.2982C10.3661 5.57894 10.4211 5.63392 10.4211 5.70175V12.5439C10.4211 12.6117 10.3661 12.6667 10.2982 12.6667H3.45614C3.38832 12.6667 3.33333 12.6117 3.33333 12.5439V5.70175Z" fill="currentColor"></path> </svg>
复制
</button>
</div>
<div class="code-block-inner-tab-content doc-tab-content-wrapper">
<input id="req-code-side-radio-0" class="doc-tab-item-radio" type="radio" name="req-code-side" checked="" data-index="0">
<div class="doc-tab-content">
<pre class="shiki github-dark-default" style="background-color:#0d1117;color:#e6edf3" tabindex="0"><code><span class="line"><span style="color:#79C0FF">POST</span><span style="color:#FF7B72"> /</span><span style="color:#E6EDF3">v2</span><span style="color:#FF7B72">/</span><span style="color:#E6EDF3">ocr</span><span style="color:#FF7B72">/</span><span style="color:#E6EDF3">paddleocr </span><span style="color:#79C0FF">HTTP</span><span style="color:#FF7B72">/</span><span style="color:#79C0FF">1.1</span></span>
<span class="line"><span style="color:#FFA657">Host</span><span style="color:#E6EDF3">: qianfan.baidubce.com</span></span>
<span class="line"><span style="color:#FFA657">Authorization</span><span style="color:#E6EDF3">: Bearer </span><span style="color:#FF7B72">&lt;</span><span style="color:#79C0FF">API</span><span style="color:#E6EDF3"> Key</span><span style="color:#FF7B72">&gt;</span></span>
<span class="line"><span style="color:#E6EDF3">Content</span><span style="color:#FF7B72">-</span><span style="color:#FFA657">Type</span><span style="color:#E6EDF3">: application</span><span style="color:#FF7B72">/</span><span style="color:#E6EDF3">json</span></span>
<span class="line"><span style="color:#E6EDF3">{</span></span>
<span class="line"><span style="color:#A5D6FF">    "model"</span><span style="color:#E6EDF3">:</span><span style="color:#A5D6FF">"paddleocr-vl-0.9b"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "file"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"https://****.com/image1.jpg"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "fileType"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">1</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "useChartRecognition"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "useDocUnwarping"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "useLayoutDetection"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "layoutNms"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "repetitionPenalty"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">1.0</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "temperature"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">0</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "topP"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">1.0</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "minPixels"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">147384</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "maxPixels"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">2822400</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "visualize"</span><span style="color:#E6EDF3">:</span><span style="color:#79C0FF">true</span></span>
<span class="line"><span style="color:#E6EDF3">}    </span></span></code></pre>
</div>
</div>
</div>
</div>
</div>
</section>
<section id="example-code">
<h2 class="section-title" id="示例代码">示例代码<a class="anchor" aria-label="示例代码 permalink" href="#%E7%A4%BA%E4%BE%8B%E4%BB%A3%E7%A0%81"></a></h2>
<div class="doc-tab-content-wrapper">
<input id="req-example-tab-0" class="doc-tab-item-radio" type="radio" name="req-example-radio-group" checked="" data-index="0">
<div class="doc-tab-content">
<div class="code-block ">
<div class="code-block-header">
<div class="code-block-name ">请求示例</div>
<button class="code-copy-btn" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="请求示例 代码" data-track-value="复制" data-tooltip-text="">
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none"> <path fill-rule="evenodd" clip-rule="evenodd" d="M5.57894 3.45614C5.57894 3.38832 5.63392 3.33333 5.70175 3.33333H12.5439C12.6117 3.33333 12.6667 3.38832 12.6667 3.45614V10.2982C12.6667 10.3661 12.6117 10.4211 12.5439 10.4211H11.7544V5.70175C11.7544 4.89754 11.1025 4.24561 10.2982 4.24561H5.57894V3.45614ZM4.24561 4.24561V3.45614C4.24561 2.65194 4.89754 2 5.70175 2H12.5439C13.3481 2 14 2.65194 14 3.45614V10.2982C14 11.1025 13.3481 11.7544 12.5439 11.7544H11.7544V12.5439C11.7544 13.3481 11.1025 14 10.2982 14H3.45614C2.65194 14 2 13.3481 2 12.5439V5.70175C2 4.89754 2.65194 4.24561 3.45614 4.24561H4.24561ZM3.33333 5.70175C3.33333 5.63392 3.38832 5.57894 3.45614 5.57894H10.2982C10.3661 5.57894 10.4211 5.63392 10.4211 5.70175V12.5439C10.4211 12.6117 10.3661 12.6667 10.2982 12.6667H3.45614C3.38832 12.6667 3.33333 12.6117 3.33333 12.5439V5.70175Z" fill="currentColor"></path> </svg>
复制
</button>
</div>
<div class="code-block-inner-tab-content doc-tab-content-wrapper">
<input id="req-example-code-0-radio-0" class="doc-tab-item-radio" type="radio" name="req-example-code-0" checked="" data-index="0">
<div class="doc-tab-content">
<pre class="shiki github-dark-default" style="background-color:#0d1117;color:#e6edf3" tabindex="0"><code><span class="line"><span style="color:#FFA657">curl</span><span style="color:#A5D6FF"> https://qianfan.baidubce.com/v2/ocr/paddleocr</span><span style="color:#FF7B72"> \</span></span>
<span class="line"><span style="color:#79C0FF">  -H</span><span style="color:#A5D6FF"> "Content-Type: application/json"</span><span style="color:#FF7B72"> \</span></span>
<span class="line"><span style="color:#79C0FF">  -H</span><span style="color:#A5D6FF"> "Authorization: Bearer &lt;API Key&gt;"</span><span style="color:#FF7B72"> \</span></span>
<span class="line"><span style="color:#79C0FF">  -d</span><span style="color:#A5D6FF"> '{</span></span>
<span class="line"><span style="color:#A5D6FF">    "model":"paddleocr-vl-0.9b",</span></span>
<span class="line"><span style="color:#A5D6FF">    "file": "https://****.com/image1.jpg",</span></span>
<span class="line"><span style="color:#A5D6FF">    "fileType": 1,</span></span>
<span class="line"><span style="color:#A5D6FF">    "useChartRecognition": true,</span></span>
<span class="line"><span style="color:#A5D6FF">    "useDocUnwarping":true,</span></span>
<span class="line"><span style="color:#A5D6FF">    "useLayoutDetection":true,</span></span>
<span class="line"><span style="color:#A5D6FF">    "layoutNms":true,</span></span>
<span class="line"><span style="color:#A5D6FF">    "repetitionPenalty":1.0,</span></span>
<span class="line"><span style="color:#A5D6FF">    "temperature":0,</span></span>
<span class="line"><span style="color:#A5D6FF">    "topP":1.0,</span></span>
<span class="line"><span style="color:#A5D6FF">    "minPixels":147384,</span></span>
<span class="line"><span style="color:#A5D6FF">    "maxPixels":2822400,</span></span>
<span class="line"><span style="color:#A5D6FF">    "visualize":true</span></span>
<span class="line"><span style="color:#A5D6FF">}'</span></span></code></pre>
</div>
</div>
</div>
</div>
</div>
</section>
<section id="response-params">
<h2 class="section-title" id="返回响应">返回响应<a class="anchor" aria-label="返回响应 permalink" href="#%E8%BF%94%E5%9B%9E%E5%93%8D%E5%BA%94"></a></h2>
<div class="horizontal-container">
<div class="horizontal-left-panel">
<div class="param-card param-card-empty">
<div class="param-card-header">
<div class="param-card-title">Headers 参数</div>
<div class="param-card-header-desc">除公共头域外，无其它特殊头域</div>
</div>
</div>
<div class="param-card">
<div class="param-card-header">
<div class="param-card-title">返回参数</div>
</div>
<div class="param-card-content">
<div class="api-param-list  ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="id" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="id">
id
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>本次请求的唯一标识，可用于排查问题。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="result" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="result">
result
</span>
<span class="api-param-list-item-type">object {2}</span>
</div>
<div class="api-param-desc"><p>输出结果。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="layoutParsingResults" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="layoutParsingResults">
layoutParsingResults
</span>
<span class="api-param-list-item-type">array</span>
</div>
<div class="api-param-desc"><p>版面解析结果。<br>
数组长度为1（对于图像输入）或实际处理的文档页数（对于PDF输入）。对于PDF输入，数组中的每个元素依次表示PDF文件中实际处理的每一页的结果。</p>
</div>
<div class="api-param-list-item-required">可选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="items" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="items">
items
</span>
<span class="api-param-list-item-type">object {4}</span>
</div>
<div class="api-param-desc"></div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="prunedResult" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="prunedResult">
prunedResult
</span>
<span class="api-param-list-item-type">object {3}</span>
</div>
<div class="api-param-desc"><p>处理后结果。<br>
对象的predict方法生成结果的 JSON 表示中res字段的简化版本，其中去除了input_path和page_index字段。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="model_settings" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="model_settings">
model_settings
</span>
<span class="api-param-list-item-type">object {4}</span>
</div>
<div class="api-param-desc"><p>配置 PaddleOCR-VL 所需的模型参数。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="use_doc_preprocessor" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="use_doc_preprocessor">
use_doc_preprocessor
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>是否启用文档预处理子产线。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="use_layout_detection" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="use_layout_detection">
use_layout_detection
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>是否启用版面检测模块。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="use_chart_recognition" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="use_chart_recognition">
use_chart_recognition
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>是否开启图标识别功能。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="format_block_content" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="format_block_content">
format_block_content
</span>
<span class="api-param-list-item-type">boolean</span>
</div>
<div class="api-param-desc"><p>是否在JSON中保存格式化后的markdown内容。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
</div>
</details>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="parsing_res_list" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="parsing_res_list">
parsing_res_list
</span>
<span class="api-param-list-item-type">array</span>
</div>
<div class="api-param-desc"><p>解析结果的列表，每个元素为一个字典，列表顺序为解析后的阅读顺序。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="items" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="items">
items
</span>
<span class="api-param-list-item-type">object {5}</span>
</div>
<div class="api-param-desc"></div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="block_label" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="block_label">
block_label
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>版面区域的边界框。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="block_content" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="block_content">
block_content
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>版面区域的标签，例如text, table等。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="block_bbox" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="block_bbox">
block_bbox
</span>
<span class="api-param-list-item-type">array</span>
</div>
<div class="api-param-desc"><p>内容为版面区域内的内容。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="items" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="items">
items
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"></div>
</div>
</div>
</details>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="block_id" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="block_id">
block_id
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>版面区域的索引，用于显示版面排序结果。<br></p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="block_order" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="block_order">
block_order
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>版面区域的顺序，用于显示版面阅读顺序。<br>
对于非排序部分，默认值为 null。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
</div>
</details>
</div>
</div>
</details>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="layout_det_res" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="layout_det_res">
layout_det_res
</span>
<span class="api-param-list-item-type">object {3}</span>
</div>
<div class="api-param-desc"><p>版面区域检测排序结果。</p>
</div>
<div class="api-param-list-item-required">可选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="boxes" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="boxes">
boxes
</span>
<span class="api-param-list-item-type">array</span>
</div>
<div class="api-param-desc"><p>预测的目标框信息，一个字典列表。每个字典代表一个检出的目标。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="items" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="items">
items
</span>
<span class="api-param-list-item-type">object {4}</span>
</div>
<div class="api-param-desc"></div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="cls_id" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="cls_id">
cls_id
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>类别 id。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="label" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="label">
label
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>类别名称。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="socre" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="socre">
socre
</span>
<span class="api-param-list-item-type">float</span>
</div>
<div class="api-param-desc"><p>目标框置信度。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="coordinate" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="coordinate">
coordinate
</span>
<span class="api-param-list-item-type">array</span>
</div>
<div class="api-param-desc"><p>目标框坐标，一个浮点数列表，格式为[xmin, ymin, xmax, ymax]。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="items" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="items">
items
</span>
<span class="api-param-list-item-type">float</span>
</div>
<div class="api-param-desc"></div>
</div>
</div>
</details>
</div>
</div>
</details>
</div>
</div>
</details>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="input_path" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="input_path">
input_path
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>输入的待预测图像的路径。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="page_index" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="page_index">
page_index
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>如果输入是PDF文件，则表示当前是PDF的第几页。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
</div>
</details>
</div>
</div>
</details>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="markdown" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="markdown">
markdown
</span>
<span class="api-param-list-item-type">object {4}</span>
</div>
<div class="api-param-desc"><p>Markdown 结果。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="text" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="text">
text
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>Markdown 文本。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="images" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="images">
images
</span>
<span class="api-param-list-item-type">object {0}</span>
</div>
<div class="api-param-desc"><p>图像键值对。<br>
key：图片相对路径。<br>
value：对应的图片链接，为JPEG格式，有效期 30 天。</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<span class="params-empty-text">暂无参数</span>
</details>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="isStart" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="isStart">
isStart
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>当前页面第一个元素是否为段开始</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="isEnd" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="isEnd">
isEnd
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>当前页面最后一个元素是否为段结束</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
</div>
</details>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="outputImages" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="outputImages">
outputImages
</span>
<span class="api-param-list-item-type">object {0}</span>
</div>
<div class="api-param-desc"><p>输出图片链接，为JPEG格式，有效期 30 天<br>
返回的预测结果是一个dict类型的数据。其中，键分别为<code>layout_det_res</code>、<code>layout_order_res</code>、<code>layout_order_res</code>和 <code>preprocessed_img</code> ，对应的值是Image.Image对象。</p>
<ul>
<li><code>layout_det_res</code>：用于显示版面区域检测的可视化图像。</li>
<li><code>layout_order_res</code>：用于显示版面阅读顺序结果的可视化图像。</li>
<li><code>preprocessed_img</code>：用于展示图像预处理的可视化图像。</li>
</ul>
</div>
<div class="api-param-list-item-required">可选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<span class="params-empty-text">暂无参数</span>
</details>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="inputImage" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="inputImage">
inputImage
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>输入图片链接，为JPEG格式，有效期 30 天。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
</div>
</details>
</div>
</div>
</details>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="datainfo" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="datainfo">
datainfo
</span>
<span class="api-param-list-item-type">object {5}</span>
</div>
<div class="api-param-desc"><p>输入数据信息</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="type" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="type">
type
</span>
<span class="api-param-list-item-type">string</span>
</div>
<div class="api-param-desc"><p>输入文件类型，可为image或pdf</p>
</div>
<div class="api-param-list-item-required" data-required="">必选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="numPages" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="numPages">
numPages
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>文件页数。<br>
当type=pdf时返回。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="pages" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="pages">
pages
</span>
<span class="api-param-list-item-type">array</span>
</div>
<div class="api-param-desc"><p>每页详细信息，包括宽高。<br>
当type=pdf时返回。</p>
</div>
<div class="api-param-list-item-required">可选</div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<div class="api-param-list api-param-list-child ">
<div class="api-param-list-item api-param-list-item-has-child">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="items" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="items">
items
</span>
<span class="api-param-list-item-type">object {0}</span>
</div>
<div class="api-param-desc"></div>
<details class="api-param-list-tree">
<summary>
<div class="api-param-list-tree-root">
<svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g><path fill-rule="evenodd" clip-rule="evenodd" d="M5.59842 7.81538L3.09594 4.71641C2.93376 4.51557 2.98209 4.2337 3.20389 4.08685C3.28912 4.03042 3.39196 4 3.49755 4L8.50249 4C8.77726 4 9 4.2017 9 4.4505C9 4.54611 8.96641 4.63923 8.90409 4.71641L6.40162 7.81538C6.23944 8.01622 5.92816 8.05998 5.70636 7.91312C5.66504 7.88576 5.62863 7.85279 5.59842 7.81538Z" fill="currentColor"></path></g></svg>
<span class="api-param-list-tree-root-text root-text-show">显示子属性</span>
<span class="api-param-list-tree-root-text root-text-hide">隐藏子属性</span>
</div>
</summary>
<span class="params-empty-text">暂无参数</span>
</details>
</div>
</div>
</details>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="width" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="width">
width
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>图片宽度。<br>
当type=image时返回。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
<div class="api-param-list-item ">
<div class="api-param-list-item-title">
<span class="api-param-list-item-name" data-copy-text="height" data-tooltip-gap="8" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="参数名" data-track-value="height">
height
</span>
<span class="api-param-list-item-type">integer</span>
</div>
<div class="api-param-desc"><p>图片高度。<br>
当type=image时返回。</p>
</div>
<div class="api-param-list-item-required">可选</div>
</div>
</div>
</details>
</div>
</div>
</details>
</div>
</div>
</div>
</div>
</div>
<div class="horizontal-right-panel">
<div class="doc-tab-group-wrapper">
<div class="doc-tab-group">
<label for="res-param-tab-0" class="doc-tab-item doc-tab-item-selected" data-index="0">
<span class="doc-tab-item-label">正确响应示例</span>
</label>
<label for="res-param-tab-1" class="doc-tab-item " data-index="1">
<span class="doc-tab-item-label">错误响应示例</span>
</label>
<div class="doc-tab-thumb"></div>
</div>
</div>
<div class="doc-tab-content-wrapper">
<input id="res-param-tab-0" class="doc-tab-item-radio" type="radio" name="res-param-radio-group" checked="" data-index="0">
<div class="doc-tab-content">
<div class="code-block ">
<div class="code-block-header">
<div class="code-block-name code-block-language-name">JSON</div>
<button class="code-copy-btn" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="JSON 代码" data-track-value="复制" data-tooltip-text="">
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none"> <path fill-rule="evenodd" clip-rule="evenodd" d="M5.57894 3.45614C5.57894 3.38832 5.63392 3.33333 5.70175 3.33333H12.5439C12.6117 3.33333 12.6667 3.38832 12.6667 3.45614V10.2982C12.6667 10.3661 12.6117 10.4211 12.5439 10.4211H11.7544V5.70175C11.7544 4.89754 11.1025 4.24561 10.2982 4.24561H5.57894V3.45614ZM4.24561 4.24561V3.45614C4.24561 2.65194 4.89754 2 5.70175 2H12.5439C13.3481 2 14 2.65194 14 3.45614V10.2982C14 11.1025 13.3481 11.7544 12.5439 11.7544H11.7544V12.5439C11.7544 13.3481 11.1025 14 10.2982 14H3.45614C2.65194 14 2 13.3481 2 12.5439V5.70175C2 4.89754 2.65194 4.24561 3.45614 4.24561H4.24561ZM3.33333 5.70175C3.33333 5.63392 3.38832 5.57894 3.45614 5.57894H10.2982C10.3661 5.57894 10.4211 5.63392 10.4211 5.70175V12.5439C10.4211 12.6117 10.3661 12.6667 10.2982 12.6667H3.45614C3.38832 12.6667 3.33333 12.6117 3.33333 12.5439V5.70175Z" fill="currentColor"></path> </svg>
复制
</button>
</div>
<div class="code-block-inner-tab-content doc-tab-content-wrapper">
<input id="res-param-code-0-radio-0" class="doc-tab-item-radio" type="radio" name="res-param-code-0" checked="" data-index="0">
<div class="doc-tab-content">
<pre class="shiki github-dark-default" style="background-color:#0d1117;color:#e6edf3" tabindex="0"><code><span class="line"><span style="color:#E6EDF3">{</span></span>
<span class="line"><span style="color:#A5D6FF">    "id"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"as-****yda9"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">    "result"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">        "layoutParsingResults"</span><span style="color:#E6EDF3">: [</span></span>
<span class="line"><span style="color:#E6EDF3">            {</span></span>
<span class="line"><span style="color:#A5D6FF">                "prunedResult"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">                    "model_settings"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">                        "use_doc_preprocessor"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                        "use_layout_detection"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                        "use_chart_recognition"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">true</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                        "format_block_content"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">false</span></span>
<span class="line"><span style="color:#E6EDF3">                    },</span></span>
<span class="line"><span style="color:#A5D6FF">                    "parsing_res_list"</span><span style="color:#E6EDF3">: [</span></span>
<span class="line"><span style="color:#E6EDF3">                        {</span></span>
<span class="line"><span style="color:#A5D6FF">                            "block_label"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"image"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                            "block_content"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">""</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                            "block_bbox"</span><span style="color:#E6EDF3">: [</span></span>
<span class="line"><span style="color:#79C0FF">                                0</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#79C0FF">                                2</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#79C0FF">                                800</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#79C0FF">                                531</span></span>
<span class="line"><span style="color:#E6EDF3">                            ],</span></span>
<span class="line"><span style="color:#A5D6FF">                            "block_id"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">0</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                            "block_order"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">null</span></span>
<span class="line"><span style="color:#E6EDF3">                        }</span></span>
<span class="line"><span style="color:#E6EDF3">                    ],</span></span>
<span class="line"><span style="color:#A5D6FF">                    "layout_det_res"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">                        "boxes"</span><span style="color:#E6EDF3">: [</span></span>
<span class="line"><span style="color:#E6EDF3">                            {</span></span>
<span class="line"><span style="color:#A5D6FF">                                "cls_id"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">14</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                                "label"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"image"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                                "score"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">0.5476956963539124</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                                "coordinate"</span><span style="color:#E6EDF3">: [</span></span>
<span class="line"><span style="color:#79C0FF">                                    0.01312255859375</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#79C0FF">                                    2.161956787109375</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#79C0FF">                                    800</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#79C0FF">                                    531</span></span>
<span class="line"><span style="color:#E6EDF3">                                ]</span></span>
<span class="line"><span style="color:#E6EDF3">                            }</span></span>
<span class="line"><span style="color:#E6EDF3">                        ]</span></span>
<span class="line"><span style="color:#E6EDF3">                    }</span></span>
<span class="line"><span style="color:#E6EDF3">                },</span></span>
<span class="line"><span style="color:#A5D6FF">                "markdown"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">                    "text"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"&lt;div style=</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF">text-align: center;</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF">&gt;&lt;img src=</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF">imgs/img_in_image_box_0_2_800_531.jpg</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF"> alt=</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF">Image</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF"> width=</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF">100%</span><span style="color:#FF7B72">\"</span><span style="color:#A5D6FF"> /&gt;&lt;/div&gt;</span><span style="color:#FF7B72">\n</span><span style="color:#A5D6FF">"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                    "images"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">                        "imgs/img_in_image_box_0_2_800_531.jpg"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"http://***/ocr/paddleocr/a5917dda-fc8d-4406-7b71-d9220befe416.jpeg"</span></span>
<span class="line"><span style="color:#E6EDF3">                    }</span></span>
<span class="line"><span style="color:#E6EDF3">                },</span></span>
<span class="line"><span style="color:#A5D6FF">                "outputImages"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">                    "layout_det_res"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"http://***/ocr/paddleocr/76a8647c-1b22-4e1b-6f83-a44b5711b4b6.jpeg"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                    "layout_order_res"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"http://***/ocr/paddleocr/6043f57f-708c-4c7d-634e-e6124d1d7f11.jpeg"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">                    "preprocessed_img"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"http://***/ocr/paddleocr/c7f92081-d403-4189-79c9-931f9883de03.jpeg"</span></span>
<span class="line"><span style="color:#E6EDF3">                },</span></span>
<span class="line"><span style="color:#A5D6FF">                "inputImage"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"http://***/ocr/paddleocr/91f300cb-8e7a-43ae-544b-a683c5e52185.jpeg"</span></span>
<span class="line"><span style="color:#E6EDF3">            }</span></span>
<span class="line"><span style="color:#E6EDF3">        ],</span></span>
<span class="line"><span style="color:#A5D6FF">        "dataInfo"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">            "type"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"image"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">            "width"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">800</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">            "height"</span><span style="color:#E6EDF3">: </span><span style="color:#79C0FF">531</span></span>
<span class="line"><span style="color:#E6EDF3">        }</span></span>
<span class="line"><span style="color:#E6EDF3">    }</span></span>
<span class="line"><span style="color:#E6EDF3">}</span></span></code></pre>
</div>
</div>
</div>
</div>
<input id="res-param-tab-1" class="doc-tab-item-radio" type="radio" name="res-param-radio-group" data-index="1">
<div class="doc-tab-content">
<div class="code-block ">
<div class="code-block-header">
<div class="code-block-name code-block-language-name">JSON</div>
<button class="code-copy-btn" data-track-category="千帆AI应用开发者中心-API参考" data-track-name="JSON 代码" data-track-value="复制" data-tooltip-text="">
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none"> <path fill-rule="evenodd" clip-rule="evenodd" d="M5.57894 3.45614C5.57894 3.38832 5.63392 3.33333 5.70175 3.33333H12.5439C12.6117 3.33333 12.6667 3.38832 12.6667 3.45614V10.2982C12.6667 10.3661 12.6117 10.4211 12.5439 10.4211H11.7544V5.70175C11.7544 4.89754 11.1025 4.24561 10.2982 4.24561H5.57894V3.45614ZM4.24561 4.24561V3.45614C4.24561 2.65194 4.89754 2 5.70175 2H12.5439C13.3481 2 14 2.65194 14 3.45614V10.2982C14 11.1025 13.3481 11.7544 12.5439 11.7544H11.7544V12.5439C11.7544 13.3481 11.1025 14 10.2982 14H3.45614C2.65194 14 2 13.3481 2 12.5439V5.70175C2 4.89754 2.65194 4.24561 3.45614 4.24561H4.24561ZM3.33333 5.70175C3.33333 5.63392 3.38832 5.57894 3.45614 5.57894H10.2982C10.3661 5.57894 10.4211 5.63392 10.4211 5.70175V12.5439C10.4211 12.6117 10.3661 12.6667 10.2982 12.6667H3.45614C3.38832 12.6667 3.33333 12.6117 3.33333 12.5439V5.70175Z" fill="currentColor"></path> </svg>
复制
</button>
</div>
<div class="code-block-inner-tab-content doc-tab-content-wrapper">
<input id="res-param-code-1-radio-0" class="doc-tab-item-radio" type="radio" name="res-param-code-1" checked="" data-index="0">
<div class="doc-tab-content">
<pre class="shiki github-dark-default" style="background-color:#0d1117;color:#e6edf3" tabindex="0"><code><span class="line"><span style="color:#E6EDF3">{</span></span>
<span class="line"><span style="color:#A5D6FF">    "error"</span><span style="color:#E6EDF3">: {</span></span>
<span class="line"><span style="color:#A5D6FF">        "code"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"invalid_argument"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">        "message"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"fetch object failed"</span><span style="color:#E6EDF3">,</span></span>
<span class="line"><span style="color:#A5D6FF">        "type"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"invalid_request_error"</span></span>
<span class="line"><span style="color:#E6EDF3">    },</span></span>
<span class="line"><span style="color:#A5D6FF">    "id"</span><span style="color:#E6EDF3">: </span><span style="color:#A5D6FF">"as-xpv8aftdfq"</span></span>
<span class="line"><span style="color:#E6EDF3">}</span></span></code></pre>
</div>
</div>
</div>
</div>
</div>
</div>
</div>
</section>
<section id="other-content">
<h2 id="错误码">错误码<a class="anchor" aria-label="错误码 permalink" href="#%E9%94%99%E8%AF%AF%E7%A0%81"></a></h2>
<p>如果调用失败并返回报错信息，请参见错误码进行解决。</p>
<p>公共错误码：<a href="https://cloud.baidu.com/doc/qianfan-api/s/Om9b4yj3w" target="_blank">查看公共错误码</a></p>
<p>本接口专有错误码如下：</p>
<table>
<thead>
<tr>
<th>HTTP状态码</th>
<th>类型</th>
<th>错误码</th>
<th>错误信息</th>
<th>说明</th>
</tr>
</thead>
<tbody>
<tr>
<td>500</td>
<td>internal_error</td>
<td>ocr_internal_error</td>
<td>返回的具体错误信息</td>
<td>ocr内部错误</td>
</tr>
<tr>
<td>400</td>
<td>invalid_request_error</td>
<td>invalid_argument</td>
<td>返回的具体错误信息</td>
<td>参数报错</td>
</tr>
<tr>
<td>401</td>
<td>invalid_request_error</td>
<td>invalid_model</td>
<td>No permission to use the model</td>
<td>model鉴权失败，该用户没有使用这个model的权限</td>
</tr>
<tr>
<td>500</td>
<td>invalid_request_error</td>
<td>invalid_model</td>
<td>Model is empty</td>
<td>未指定model参数</td>
</tr>
</tbody>
</table>
</section>
</main>