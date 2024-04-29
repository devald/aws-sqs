(ns doc)

(daws/doc :sqs :ReceiveMessage)
-------------------------
ReceiveMessage

Retrieves one or more messages (up to 10), from the specified queue.
Using the `WaitTimeSeconds` parameter enables long-poll support. For
more information, see [Amazon SQS Long Polling][] in the `Amazon SQS
Developer Guide`.

Short poll is the default behavior where a weighted random set of
machines is sampled on a `ReceiveMessage` call. Thus, only the messages
on the sampled machines are returned. If the number of messages in the
queue is small (fewer than 1,000), you most likely get fewer messages
than you requested per `ReceiveMessage` call. If the number of messages
in the queue is extremely small, you might not receive any messages in a
particular `ReceiveMessage` response. If this happens, repeat the
request.

For each message returned, the response includes the following:

* The message body.

* An MD5 digest of the message body. For information about MD5, see
[RFC1321][].

* The `MessageId` you received when you sent the message to the
queue.

* The receipt handle.

* The message attributes.

* An MD5 digest of the message attributes.



The receipt handle is the identifier you must provide when deleting the
message. For more information, see [Queue and Message Identifiers][] in
the `Amazon SQS Developer Guide`.

You can provide the `VisibilityTimeout` parameter in your request. The
parameter is applied to the messages that Amazon SQS returns in the
response. If you don't include the parameter, the overall visibility
timeout for the queue is used for the returned messages. For more
information, see [Visibility Timeout][] in the `Amazon SQS Developer
Guide`.

A message that isn't deleted or a message whose visibility isn't
extended before the visibility timeout expires counts as a failed
receive. Depending on the configuration of the queue, the message might
be sent to the dead-letter queue.

=== Note ===
In the future, new attributes might be added. If you write code that
calls this action, we recommend that you structure your code so that it
can handle new attributes gracefully.

============

[Amazon SQS Long Polling]:
https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-long-polling.html

[RFC1321]:
https://www.ietf.org/rfc/rfc1321.txt

[Queue and Message Identifiers]:
https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-message-identifiers.html

[Visibility Timeout]:
https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html


-------------------------
Request

{:AttributeNames [:seq-of
                  [:one-of
                   ["All"
                    "Policy"
                    "VisibilityTimeout"
                    "MaximumMessageSize"
                    "MessageRetentionPeriod"
                    "ApproximateNumberOfMessages"
                    "ApproximateNumberOfMessagesNotVisible"
                    "CreatedTimestamp"
                    "LastModifiedTimestamp"
                    "QueueArn"
                    "ApproximateNumberOfMessagesDelayed"
                    "DelaySeconds"
                    "ReceiveMessageWaitTimeSeconds"
                    "RedrivePolicy"
                    "FifoQueue"
                    "ContentBasedDeduplication"
                    "KmsMasterKeyId"
                    "KmsDataKeyReusePeriodSeconds"
                    "DeduplicationScope"
                    "FifoThroughputLimit"
                    "RedriveAllowPolicy"
                    "SqsManagedSseEnabled"]]],
 :MaxNumberOfMessages integer,
 :MessageAttributeNames [:seq-of string],
 :QueueUrl string,
 :ReceiveRequestAttemptId string,
 :VisibilityTimeout integer,
 :WaitTimeSeconds integer}

Required

[:QueueUrl]

-------------------------
Response

{:Messages [:seq-of
            {:Attributes [:map-of
                          [:one-of
                           ["SenderId"
                            "SentTimestamp"
                            "ApproximateReceiveCount"
                            "ApproximateFirstReceiveTimestamp"
                            "SequenceNumber"
                            "MessageDeduplicationId"
                            "MessageGroupId"
                            "AWSTraceHeader"
                            "DeadLetterQueueSourceArn"]]
                          string],
             :Body string,
             :MD5OfBody string,
             :MD5OfMessageAttributes string,
             :MessageAttributes [:map-of
                                 string
                                 {:BinaryListValues [:seq-of blob],
                                  :BinaryValue blob,
                                  :DataType string,
                                  :StringListValues [:seq-of string],
                                  :StringValue string}],
             :MessageId string,
             :ReceiptHandle string}]}



(daws/doc :sqs :DeleteMessageBatch)
-------------------------
DeleteMessageBatch

Deletes up to ten messages from the specified queue. This is a batch
version of ` [DeleteMessage][].` The result of the action on each
message is reported individually in the response.

{:tag :important, :attrs {}, :content (" " ["Because the batch request
can result in a combination of successful and unsuccessful actions, you
should check for batch errors even when the call returns an HTTP status
code of " ["`" "200" "`"] "." "\n\n"] " ")}

[DeleteMessage]:



-------------------------
Request

{:Entries [:seq-of {:Id string, :ReceiptHandle string}], :QueueUrl string}

Required

[:QueueUrl :Entries]

-------------------------
Response

{:Failed [:seq-of
          {:Code string, :Id string, :Message string, :SenderFault boolean}],
 :Successful [:seq-of {:Id string}]}

=> nil
(daws/doc :sqs :SendMessageBatch)
-------------------------
SendMessageBatch

You can use `SendMessageBatch` to send up to 10 messages to the
specified queue by assigning either identical or different values to
each message (or by not assigning values at all). This is a batch
version of ` [SendMessage][].` For a FIFO queue, multiple messages
within a single batch are enqueued in the order they are sent.

The result of sending each message is reported individually in the
response. Because the batch request can result in a combination of
successful and unsuccessful actions, you should check for batch errors
even when the call returns an HTTP status code of `200`.

The maximum allowed individual message size and the maximum total
payload size (the sum of the individual lengths of all of the batched
                  messages) are both 256 KiB (262,144 bytes).

{:tag :important, :attrs {}, :content (" " ["A message can include only
XML, JSON, and unformatted text. The following Unicode characters are
allowed:" "\n\n"] " " [" " ["`" "#x9" "`"] " | " ["`" "#xA" "`"] " | "
                                                                   ["`" "#xD" "`"] " | " ["`" "#x20" "`"] " to " ["`" "#xD7FF" "`"] " | "
                                                                   ["`" "#xE000" "`"] " to " ["`" "#xFFFD" "`"] " | " ["`" "#x10000" "`"] "
to " ["`" "#x10FFFF" "`"] " " "\n\n"] " " ["Any characters not included
in this list will be rejected. For more information, see the " ["[" "W3C
specification for characters" "][]"] "." "\n\n"] " ")} If you don't
specify the `DelaySeconds` parameter for an entry, Amazon SQS uses the
default value for the queue.

[SendMessage]:


[W3C specification for characters]:
http://www.w3.org/TR/REC-xml/#charsets


-------------------------
Request

{:Entries [:seq-of
           {:DelaySeconds integer,
            :Id string,
            :MessageAttributes [:map-of
                                string
                                {:BinaryListValues [:seq-of blob],
                                 :BinaryValue blob,
                                 :DataType string,
                                 :StringListValues [:seq-of string],
                                 :StringValue string}],
            :MessageBody string,
            :MessageDeduplicationId string,
            :MessageGroupId string,
            :MessageSystemAttributes [:map-of
                                      [:one-of ["AWSTraceHeader"]]
                                      {:BinaryListValues [:seq-of blob],
                                       :BinaryValue blob,
                                       :DataType string,
                                       :StringListValues [:seq-of string],
                                       :StringValue string}]}],
 :QueueUrl string}

Required

[:QueueUrl :Entries]

-------------------------
Response

{:Failed [:seq-of
          {:Code string, :Id string, :Message string, :SenderFault boolean}],
 :Successful [:seq-of
              {:Id string,
               :MD5OfMessageAttributes string,
               :MD5OfMessageBody string,
               :MD5OfMessageSystemAttributes string,
               :MessageId string,
               :SequenceNumber string}]}
