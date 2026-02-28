"""
多 Agent 竞价系统 —— 用 AutoGen SelectorGroupChat 实现

场景：用户想买一台笔记本电脑，3 家卖家 Agent 轮流竞价，
     拍卖官 Agent 主持流程，买家 Agent 最终做出选择。

竞价流程：
  1. 买家发布需求
  2. 拍卖官宣布竞价开始，介绍规则
  3. 三个卖家轮流出价（第一轮）
  4. 拍卖官总结第一轮，宣布进入第二轮
  5. 卖家根据对手报价调整（第二轮）
  6. 拍卖官做最终总结，请买家决策
  7. 买家宣布中标者，说 DEAL_DONE 结束
"""

import asyncio
import random
from typing import Sequence

from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.conditions import TextMentionTermination, MaxMessageTermination
from autogen_agentchat.messages import BaseAgentEvent, BaseChatMessage
from autogen_agentchat.teams import SelectorGroupChat
from autogen_agentchat.ui import Console
from autogen_core.models import ModelFamily
from autogen_ext.models.openai import OpenAIChatCompletionClient

# ━━━━━━━━━━━━━━━━ 模型客户端 ━━━━━━━━━━━━━━━━

qwen = OpenAIChatCompletionClient(
    model="qwen-plus",
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    api_key="sk-78c98ba04206418babc522196aef5712",
    model_info={
        "vision": False,
        "function_calling": True,
        "json_output": True,
        "family": ModelFamily.UNKNOWN,
        "structured_output": False,
    },
)

# ━━━━━━━━━━━━━━━━ 工具函数 ━━━━━━━━━━━━━━━━

BIDS: dict[str, list[dict]] = {}


async def submit_bid(seller_name: str, product_name: str, price: float,
                     warranty_years: int, highlights: str) -> str:
    """提交一份竞价报价单。
    Args:
        seller_name: 卖家名称
        product_name: 商品型号名称
        price: 报价（元）
        warranty_years: 保修年限
        highlights: 核心卖点（一句话）
    """
    bid = {
        "product": product_name,
        "price": price,
        "warranty": warranty_years,
        "highlights": highlights,
    }
    BIDS.setdefault(seller_name, []).append(bid)
    round_no = len(BIDS[seller_name])
    return (f"✅ 报价已提交！[第{round_no}轮] {seller_name} 出价 ¥{price:.0f} "
            f"| {product_name} | 保修{warranty_years}年 | {highlights}")


async def announce_winner(winner: str, reason: str) -> str:
    """买家宣布最终中标者。
    Args:
        winner: 中标卖家名称
        reason: 选择理由
    """
    return f"🏆 中标结果：{winner}！\n理由：{reason}\n\nDEAL_DONE"


# ━━━━━━━━━━━━━━━━ 卖家 Agent ━━━━━━━━━━━━━━━━

seller_a = AssistantAgent(
    name="Lenovo_agent",
    model_client=qwen,
    tools=[submit_bid],
    reflect_on_tool_use=True,
    description="联想笔记本卖家（联想专卖），擅长性价比路线，喜欢用低价策略抢客户。",
    system_message=(
        "你是「联想专卖」的销售代表。\n"
        "你的竞价策略：主打性价比，价格要有竞争力，强调耐用和售后。\n"
        "规则：\n"
        "- 每次发言必须调用 submit_bid 工具提交正式报价，seller_name 填「联想专卖」\n"
        "- 报价单位是人民币元\n"
        "- 第二轮可以根据对手报价适当降价或增加赠品\n"
        "- 不要编造不存在的型号，用合理的虚拟型号名\n"
        "- 发言简洁，突出自己的优势，回复用中文"
    ),
)

seller_b = AssistantAgent(
    name="Dell_agent",
    model_client=qwen,
    tools=[submit_bid],
    reflect_on_tool_use=True,
    description="戴尔笔记本卖家（戴尔旗舰），走中高端路线，强调商务品质和服务。",
    system_message=(
        "你是「戴尔旗舰」的销售代表。\n"
        "你的竞价策略：中高端定位，强调商务品质、屏幕、散热和上门服务。\n"
        "规则：\n"
        "- 每次发言必须调用 submit_bid 工具提交正式报价，seller_name 填「戴尔旗舰」\n"
        "- 报价单位是人民币元\n"
        "- 第二轮可以根据对手报价微调，但不要降太多（保持品质感）\n"
        "- 发言简洁，突出差异化优势，回复用中文"
    ),
)

seller_c = AssistantAgent(
    name="ASUS_agent",
    model_client=qwen,
    tools=[submit_bid],
    reflect_on_tool_use=True,
    description="华硕笔记本卖家（华硕电竞），主打电竞和高性能，擅长用配置吸引人。",
    system_message=(
        "你是「华硕电竞」的销售代表。\n"
        "你的竞价策略：主打高性能电竞，强调显卡、刷新率、RGB 和电竞生态。\n"
        "规则：\n"
        "- 每次发言必须调用 submit_bid 工具提交正式报价，seller_name 填「华硕电竞」\n"
        "- 报价单位是人民币元\n"
        "- 第二轮可以适当调价或加赠电竞外设\n"
        "- 发言简洁，突出电竞卖点，回复用中文"
    ),
)

# ━━━━━━━━━━━━━━━━ 拍卖官 Agent ━━━━━━━━━━━━━━━━

auctioneer = AssistantAgent(
    name="Auctioneer_agent",
    model_client=qwen,
    description="竞价流程主持人（拍卖官），负责宣布规则、总结报价、推动流程。",
    system_message=(
        "你是竞价流程的「拍卖官」，负责主持整个竞价过程。\n"
        "参与竞价的三家卖家是：联想专卖、戴尔旗舰、华硕电竞。\n"
        "你的职责：\n"
        "1. 竞价开始时，宣布规则：共两轮报价，每轮每位卖家出价一次\n"
        "2. 第一轮三家报价完成后，做一个简短总结对比表\n"
        "3. 宣布进入第二轮，提醒卖家可以调整报价\n"
        "4. 第二轮三家报价完成后，做最终总结对比表，请买家做决定\n"
        "5. 全程用中文，保持公正中立，语气专业\n"
        "注意：你不能替买家做决定，也不能偏袒任何一方"
    ),
)

# ━━━━━━━━━━━━━━━━ 买家 Agent ━━━━━━━━━━━━━━━━

buyer = AssistantAgent(
    name="Buyer_agent",
    model_client=qwen,
    tools=[announce_winner],
    reflect_on_tool_use=True,
    description="买家小王，想买笔记本的用户，负责在竞价结束后选出中标者。",
    system_message=(
        "你是买家「小王」，你想买一台笔记本电脑。\n"
        "预算：5000-8000 元，主要用途：日常办公 + 偶尔玩游戏。\n"
        "你的决策标准（按优先级）：\n"
        "1. 性价比（同等配置谁更便宜）\n"
        "2. 售后保障（保修年限）\n"
        "3. 品牌口碑和特色功能\n"
        "注意：\n"
        "- 在拍卖官做完最终总结并请你决定之前，不要提前做决定\n"
        "- 做决定时，必须调用 announce_winner 工具宣布结果\n"
        "- 用中文回复"
    ),
)

# ━━━━━━━━━━━━━━━━ 发言顺序控制（每轮卖家随机） ━━━━━━━━━━━━━━━━

SELLERS = ["Lenovo_agent", "Dell_agent", "ASUS_agent"]

def _build_turn_order() -> list[str]:
    """生成完整发言序列：整体流程固定，每轮卖家出价顺序随机打乱。"""
    round_1 = random.sample(SELLERS, len(SELLERS))
    round_2 = random.sample(SELLERS, len(SELLERS))
    order = (
        ["Auctioneer_agent"]   # 拍卖官开场
        + round_1              # 第一轮（随机顺序）
        + ["Auctioneer_agent"] # 第一轮总结
        + round_2              # 第二轮（再次随机）
        + ["Auctioneer_agent"] # 最终总结
        + ["Buyer_agent"]      # 买家拍板
    )
    print(f"🎲 本次发言顺序：{' → '.join(order)}")
    return order

TURN_ORDER = _build_turn_order()


def bidding_selector(messages: Sequence[BaseAgentEvent | BaseChatMessage]) -> str | None:
    """自定义选择函数：按随机生成的顺序轮流发言。
    只统计 BaseChatMessage 数量来决定该谁说话。"""
    chat_count = sum(1 for m in messages if isinstance(m, BaseChatMessage))
    if chat_count < len(TURN_ORDER):
        return TURN_ORDER[chat_count]
    return "Buyer_agent"


# ━━━━━━━━━━━━━━━━ 组装团队 ━━━━━━━━━━━━━━━━

team = SelectorGroupChat(
    participants=[auctioneer, seller_a, seller_b, seller_c, buyer],
    model_client=qwen,
    selector_func=bidding_selector,
    termination_condition=(
        TextMentionTermination("DEAL_DONE") | MaxMessageTermination(20)
    ),
)

# ━━━━━━━━━━━━━━━━ 运行 ━━━━━━━━━━━━━━━━


async def main():
    print("=" * 60)
    print("       🔨  多 Agent 竞价系统  🔨")
    print("=" * 60)
    print()

    result = await Console(team.run_stream(
        task="我想买一台笔记本电脑，预算 5000-8000 元，日常办公 + 偶尔玩游戏，请开始竞价！"
    ))

    print("\n" + "=" * 60)
    print("       📊  竞价记录汇总  📊")
    print("=" * 60)
    for seller, bids in BIDS.items():
        for i, bid in enumerate(bids, 1):
            print(f"  [{seller}] 第{i}轮: ¥{bid['price']:.0f} | "
                  f"{bid['product']} | 保修{bid['warranty']}年 | {bid['highlights']}")
    print("=" * 60)

    await qwen.close()


asyncio.run(main())
