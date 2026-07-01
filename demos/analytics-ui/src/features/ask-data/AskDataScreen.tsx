import { AlertTriangle, Bot, Database, Send, Sparkles, UserRound } from "lucide-react";
import { useMemo, useState } from "react";
import type { AppSnapshot, ChatResponse } from "../../api/analyticsApi";
import { askData } from "../../api/analyticsApi";
import { ResultTable } from "../../components/ResultTable";
import { fileName, formatValue } from "../../lib/format";

type AskDataScreenProps = {
  snapshot: AppSnapshot;
};

type ChatMessage = {
  id: string;
  role: "assistant" | "user";
  text: string;
  response?: ChatResponse;
};

const tableKeys = [
  "features",
  "correlations",
  "rows",
  "groups",
  "columns",
  "forecast",
  "anomalies",
  "featureImportance"
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function rowsFromResult(result: unknown): { title: string; rows: Array<Record<string, unknown>> } | null {
  if (!isRecord(result)) {
    return null;
  }
  for (const key of tableKeys) {
    const value = result[key];
    if (Array.isArray(value) && value.every(isRecord)) {
      return { title: key, rows: value };
    }
  }
  return null;
}

function metricRows(result: unknown): Array<Record<string, unknown>> {
  if (!isRecord(result)) {
    return [];
  }
  return Object.entries(result)
    .filter(([, value]) => typeof value === "string" || typeof value === "number" || typeof value === "boolean")
    .map(([metric, value]) => ({ metric, value }));
}

function ResultEvidence({ response }: { response: ChatResponse }) {
  if (response.error) {
    return (
      <div className="chat-warning">
        <AlertTriangle size={16} />
        {response.error}
      </div>
    );
  }

  const resultTable = rowsFromResult(response.result);
  const metrics = metricRows(response.result);

  return (
    <div className="evidence-stack">
      {metrics.length > 0 && (
        <div>
          <div className="section-heading compact">
            <h3>Result Summary</h3>
            <span className="pill">{metrics.length} fields</span>
          </div>
          <ResultTable rows={metrics} />
        </div>
      )}

      {resultTable ? (
        <div>
          <div className="section-heading compact">
            <h3>{resultTable.title}</h3>
            <span className="pill">{resultTable.rows.length} rows</span>
          </div>
          <ResultTable rows={resultTable.rows} />
        </div>
      ) : (
        <pre className="json-block">{JSON.stringify(response.result ?? {}, null, 2)}</pre>
      )}
    </div>
  );
}

function nextId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function AskDataScreen({ snapshot }: AskDataScreenProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      text: "Ask a business or statistical question about the active dataset. I will run the matching analytics tool and show the result evidence."
    }
  ]);
  const [question, setQuestion] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const datasetLabel = useMemo(() => fileName(snapshot.session?.csv), [snapshot.session?.csv]);
  const roles = useMemo(() => {
    const source = snapshot.dashboard?.source;
    const candidate = source && typeof source === "object" ? source.roles : undefined;
    return candidate && typeof candidate === "object" ? (candidate as Record<string, unknown>) : {};
  }, [snapshot.dashboard?.source]);
  const primaryMetric = String(roles.primaryMetric ?? "the primary metric");
  const secondaryMetric = String(roles.secondaryMetric ?? "the secondary metric");
  const segment = String(roles.segment ?? "the main segment");
  const treatment = String(roles.treatment ?? "the treatment column");
  const quickQuestions = useMemo(
    () => [
      `What features are important for ${primaryMetric}?`,
      `Show correlations for ${primaryMetric}.`,
      `Does ${treatment} cause ${primaryMetric}?`,
      `Average ${primaryMetric} by ${segment}.`,
      `What correlates with ${secondaryMetric}?`
    ],
    [primaryMetric, secondaryMetric, segment, treatment]
  );

  async function submitQuestion(nextQuestion = question) {
    const trimmed = nextQuestion.trim();
    if (!trimmed || submitting) {
      return;
    }

    setQuestion("");
    setSubmitting(true);
    setMessages((current) => [...current, { id: nextId(), role: "user", text: trimmed }]);

    try {
      const response = await askData(trimmed);
      setMessages((current) => [
        ...current,
        {
          id: nextId(),
          role: "assistant",
          text: response.answer,
          response
        }
      ]);
    } catch (error) {
      setMessages((current) => [
        ...current,
        {
          id: nextId(),
          role: "assistant",
          text: error instanceof Error ? error.message : "The analytics API could not answer that question.",
          response: {
            question: trimmed,
            answer: "The analytics API could not answer that question.",
            error: error instanceof Error ? error.message : "Unknown chat error."
          }
        }
      ]);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="ask-layout">
      <section className="panel ask-context">
        <div>
          <p className="eyebrow">Ask Data</p>
          <h2>{datasetLabel}</h2>
          <p className="muted">
            {formatValue(snapshot.session?.rows)} rows available for local analytics tools and optional Ollama summaries.
          </p>
        </div>
        <div className="ask-context-stat">
          <Database size={17} />
          <span>{snapshot.filterOptions?.filters.length ?? 0} generated filters</span>
        </div>
      </section>

      <section className="panel chat-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Conversation</p>
            <h3>Question To Evidence</h3>
          </div>
          <span className="pill">{submitting ? "Running tool" : "Ready"}</span>
        </div>

        <div className="prompt-row" aria-label="Example questions">
          {quickQuestions.map((prompt) => (
            <button
              className="prompt-button"
              disabled={submitting}
              key={prompt}
              onClick={() => void submitQuestion(prompt)}
              type="button"
            >
              <Sparkles size={14} />
              {prompt}
            </button>
          ))}
        </div>

        <div className="chat-thread">
          {messages.map((message) => (
            <article className={`chat-message ${message.role}`} key={message.id}>
              <div className="message-avatar">{message.role === "assistant" ? <Bot size={16} /> : <UserRound size={16} />}</div>
              <div className="message-body">
                <p>{message.text}</p>
                {message.response && (
                  <>
                    <div className="trace-row">
                      <span>tool: {message.response.tool ?? "none"}</span>
                      <span>model: {message.response.model ?? "local_tool_router"}</span>
                      <span>question: {message.response.question}</span>
                    </div>
                    <ResultEvidence response={message.response} />
                  </>
                )}
              </div>
            </article>
          ))}
        </div>

        <form
          className="question-box"
          onSubmit={(event) => {
            event.preventDefault();
            void submitQuestion();
          }}
        >
          <textarea
            aria-label="Ask a question about the dataset"
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                event.preventDefault();
                void submitQuestion();
              }
            }}
            placeholder="Ask about drivers, correlations, causality, anomalies, forecast, regression, schema, quality, or segments."
            rows={3}
            value={question}
          />
          <button className="primary-button send-button" disabled={submitting || !question.trim()} type="submit">
            <Send size={16} />
            Ask
          </button>
        </form>
      </section>
    </div>
  );
}
